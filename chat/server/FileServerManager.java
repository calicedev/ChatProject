package chat.server;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.UUID;

public class FileServerManager extends Thread {
	
	private String FILE_SAVED_PATH = "C:\\Users\\Tmax\\upload\\";
	
	private Socket socket;
	private DataInputStream fDis;
	private DataOutputStream fDos;
	private FileInputStream fis;
	private FileOutputStream fos;
	
	// Serversocket으로 들어오는 접속을 감지하면 FileServerManager 객체 생성
	public FileServerManager(Socket socket){
		try {
			this.socket = socket;
			this.fDis = new DataInputStream(socket.getInputStream());
		} catch (Exception e) {
			closeAll();
			System.out.println("[FileServer] Connection closed: "+ socket.toString());
			FileServer.getInstance().exitUser(this);
		}
	}


	@Override
	public void run() {
		try {
			// 들어온 메세지 upload/download에 따라 실행 메소드가 다름 
			String msg = fDis.readUTF();
			JSONObject inputJsonObject = new JSONObject(msg);
			String command = inputJsonObject.getString("command");

			if("upload".equals(command)){
				upload(inputJsonObject.getString("fileOriginName"), inputJsonObject.getString("uploader"));
			} else if("download".equals(command)){
				String fileUuidName = inputJsonObject.getString("fileUuidName");
				String downloader = inputJsonObject.getString("downloader");
				if ("n".equals(inputJsonObject.getString("data"))) {
					FileServer.getInstance().checkReceivedFile(fileUuidName, downloader);
				} else {
					download(fileUuidName, downloader);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			closeAll();
		}
	}

	public void upload(String fileOriginName, String uploader) throws Exception {
		// 동일한 파일명을 가진 경우 잘못된 전송을 막기 위해 uuid를 사용
		String fileUuidName = UUID.randomUUID().toString();
		fDos = new DataOutputStream(socket.getOutputStream());
		try (FileOutputStream fos = new FileOutputStream(FILE_SAVED_PATH + fileUuidName);
			 DataInputStream fDis = new DataInputStream(socket.getInputStream())) {
			byte[] buf = new byte[1024];
			int read;
			int lastPacketNumber = 0; // 마지막으로 처리된 패킷 번호를 추적하기 위해
			long fileSize = 0;
			String fileId = null;
			String packetType = null;

			while (true) {
				String headerJson = fDis.readUTF(); // 헤더 정보 읽기

				if ("END_OF_FILE".equals(headerJson)) {
					System.out.println("File upload completed.");
					break; // 파일 전송 완료
				}

				// 클라이언트가 전해준 코드에서 패킷 번호와, 바이트 수를 가져온다.
				JSONObject header = new JSONObject(headerJson);
				int packetNumber = header.getInt("packetNumber");
				int bytes = header.getInt("bytes");

				// 만약 패킷 번호가 이전 패킷 번호의 다음 번호와 일치하지 않는 경우 재전송 로직을 수행한다. 일치하면 ACK를 전송한다.
				if (packetNumber != lastPacketNumber + 1) {
//					System.out.println("Packet out of order. Expected: " + (lastPacketNumber + 1) + ", but received: " + packetNumber);
					// 오류 처리 또는 재전송 요청
					// 클라이언트에게 ERROR 메시지를 보내 재전송 요청
					fDos.writeUTF("ERROR");
					fDos.flush();
					System.out.println("Packet " + packetNumber + " transmission failed. Retrying...");
					// 재전송 로직 추가
					while (true) {
						// 패킷 재전송
						String retransmitHeaderJson  = fDis.readUTF();
						JSONObject retransmitHeader = new JSONObject(retransmitHeaderJson);
						int retransmitPacketNumber = retransmitHeader.getInt("packetNumber");
						int retransmitBytes = retransmitHeader.getInt("bytes");

						byte[] retransmitData = new byte[retransmitPacketNumber];
						int readBytes = fDis.read(retransmitData, 0, bytes);
						fos.write(retransmitData, 0, readBytes);

						// 서버로부터 ACK 또는 ERROR 수신 대기
						String response = fDis.readUTF();
						if ("ACK".equals(response)) {
							
							System.out.println("Packet " + packetNumber + " retransmitted successfully.");
							break;
						} else {
							System.out.println("Packet " + packetNumber + " retransmission failed. Retrying...");
						}
					}
				}else{ // 패킷을 정상적으로 받았다는 ACK 전송
					fDos.writeUTF("ACK");
					fDos.flush();
				}

				// 만약 첫번째 패킷이라면 파일 ID, 파일 크기, 패킷 유형을 설정한다.
				// 첫번째 패킷이 아니라면 패킷 유형을 middle 로 설정한다.
				if (packetNumber == 1) {
					fileId = header.getString("fileId");
					fileSize = header.getLong("fileSize");
					packetType = "start";
				} else {
					packetType = "middle";
				}

				read = fDis.read(buf, 0, bytes);
				fos.write(buf, 0, read);
				lastPacketNumber = packetNumber;
			}

			// 파일 업로드 정보 기록
			HashMap<String, Object> info = new HashMap<>();
			info.put("uploader", uploader);
			info.put("fileOriginName", fileOriginName);
			info.put("fileUuidName", fileUuidName);
			info.put("fileId", fileId);
			info.put("fileSize", fileSize);
			info.put("packetType", packetType);

			FileServer.getInstance().regFile(info);
			// 서버에 파일이 새로 올라왔음을 알림
			ChatServer.getInstance().broadcastFile(uploader, fileOriginName, fileUuidName);
		}
	}


	/*public void download(String fileUuidName, String downloader) throws Exception{
		
		fDos = new DataOutputStream(socket.getOutputStream());
		// 업로드 된 파일 객체 생성
		File file = new File(FILE_SAVED_PATH + fileUuidName);
		fis = new FileInputStream(file);
		
		// 파일 전송
		byte[] buf = new byte[1024];
		int read = 0;
		while((read = fis.read(buf, 0, buf.length)) > 0){
			fDos.write(buf, 0, read);
		}
		fDos.flush();
		try { if(fis != null) {fis.close(); }} catch (IOException e) {}
		
		// 해당 파일의 사용자 리스트에서 사용자 삭제
		FileServer.getInstance().checkReceivedFile(fileUuidName, downloader);
	}
	*/

	public void download(String fileUuidName, String downloader) throws IOException {
		File file = new File(FILE_SAVED_PATH + fileUuidName);
		try (FileInputStream fis = new FileInputStream(file);
			 DataOutputStream fDos = new DataOutputStream(socket.getOutputStream())) {
			byte[] buf = new byte[1024]; // 버퍼 크기
			int read;
			int packetNumber = 0;
			long fileSize = file.length();
			String fileId = UUID.randomUUID().toString();

			while ((read = fis.read(buf)) > 0) {
				JSONObject header = new JSONObject();
				header.put("packetNumber", ++packetNumber);
				header.put("bytes", read);
				header.put("fileId", fileId);
				header.put("fileSize", fileSize);

				// 패킷 타입을 이렇게 두는 이유는 파일 전송 중에 현재 패킷의 유형을 의미합니다. start, middle로 나눠있는데
				// 파일 시작에 패킷은 파일의 기본 정보와 함께 전송됩니다. 이 정보에서 파일 크기, 파일 식별자를 구분합니다.
				header.put("packetType", packetNumber == 1 ? "start" : "middle");

				fDos.writeUTF(header.toString()); // 헤더 전송
				fDos.write(buf, 0, read); // 데이터 전송
				fDos.flush();
				String response = fDis.readUTF();
				if ("ERROR".equals(response)) {
					System.out.println("Packet " + packetNumber + " transmission failed. Retrying...");
					// 재전송 로직 추가
					while (true) {
						// 패킷 재전송
						fDos.writeUTF(header.toString()); // 헤더 전송
						fDos.write(buf, 0, read); // 데이터 전송
						fDos.flush();

						// 서버로부터 ACK 또는 ERROR 수신 대기
						response = fDis.readUTF();
						if ("ACK".equals(response)) {
							System.out.println("Packet " + packetNumber + " retransmitted successfully.");
							break;
						} else {
							System.out.println("Packet " + packetNumber + " retransmission failed. Retrying...");
						}
					}
				}
				System.out.println("Packet " + packetNumber + " sent, size: " + read + " bytes");
			}

			// 파일 전송 완료 후 종료 신호 전송
			fDos.writeUTF("END_OF_FILE");
			fDos.flush();
		}

		// 파일 다운로드 완료 후 처리
		FileServer.getInstance().checkReceivedFile(fileUuidName, downloader);
	}


	public void closeAll(){
		try { if(fis != null) {fis.close(); }} catch (IOException e) {}
		try { if(fos != null) {fos.close(); }} catch (IOException e) {}
		try { if(fDis != null) {fDis.close();}} catch (IOException e) {}
		try { if(fDos != null) {fDos.close(); }} catch (IOException e) {}
		try { if(socket != null) {socket.close(); }} catch (IOException e) {}
	}
}
