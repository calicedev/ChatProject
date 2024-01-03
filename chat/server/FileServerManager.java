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

	public void upload(String fileOriginName, String uploader) throws Exception{
		// 동일한 파일명을 가진 경우 잘못된 전송을 막기 위해 uuid를 사용
		// 파일을 다운로드하는 경우에는 uuid, userId, file name 세가지를 받아서
		// file name의 파일을 생성하고 발신자 userid가 업로드한 uuid의 파일 받음
		String fileUuidName = UUID.randomUUID().toString();
		// uuid로 된 파일저장
		File file = new File(FILE_SAVED_PATH + fileUuidName);
		try (FileOutputStream fos = new FileOutputStream(file)) {
			byte[] buf = new byte[1024];
			int read;
			int lastPacketNumber = 0;

			while (true) {
				String headerJson = fDis.readUTF(); // 헤더 정보 읽기
				if ("END_OF_FILE".equals(headerJson)) {
					System.out.println("File upload completed.");
					break; // 파일 전송 완료
				}

				JSONObject header = new JSONObject(headerJson);
				int packetNumber = header.getInt("packetNumber");
				int bytes = header.getInt("bytes");


				if (packetNumber != lastPacketNumber + 1) {
					System.out.println("Packet out of order. Expected: " + (lastPacketNumber + 1) + ", but received: " + packetNumber);
					// 오류 처리 또는 재전송 요청
					break;
				}

				read = fDis.read(buf, 0, bytes);
				fos.write(buf, 0, read);
				lastPacketNumber = packetNumber;
			}
		}
		// 파일 정보를 기록
		HashMap<String, Object> info = new HashMap<>();
		info.put("uploader", uploader);
		info.put("fileOriginName", fileOriginName);
		info.put("fileUuidName", fileUuidName);

		FileServer.getInstance().regFile(info);
		// 서버에 파일이 새로 올라왔음을 알림
		ChatServer.getInstance().broadcastFile(uploader, fileOriginName, fileUuidName);
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

			while ((read = fis.read(buf)) > 0) {
				JSONObject header = new JSONObject();
				header.put("packetNumber", ++packetNumber);
				header.put("bytes", read);

				fDos.writeUTF(header.toString()); // 헤더 전송
				fDos.write(buf, 0, read); // 데이터 전송
				fDos.flush();
				// 간단한 흐름 제어
				Thread.sleep(10);
			}

			// 파일 전송 완료 후 종료 신호 전송
			fDos.writeUTF("END_OF_FILE");
			fDos.flush();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
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
