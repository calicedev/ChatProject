package chat.client;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

import java.util.HashMap;
import java.util.Scanner;

import static chat.client.Client.ClientStatus.*;


public class Client {
	enum ClientStatus {
		NOT_LOGGED_IN, LOGGING_IN, TEXT_TRANSMITTING, FILE_TRANSMITTING, FILE_DOWNLOADING
	}
	public  static final String SERVER_ADDRESS = "localhost";
	public  static final int SERVER_PORT = 8083;
	public  static final int FILE_SERVER_PORT = 8082;

	private Socket socket;
	private DataOutputStream dos;
	private DataInputStream dis;


	private ClientStatus status;
	HashMap<String, String> fileInfo;
	String id;

	public Client() {
		this.status = NOT_LOGGED_IN;
		this.fileInfo = new HashMap<>();
		connectToServer();
		startIOThreads();
	}

	private void connectToServer() {
		try {
			this.socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void startIOThreads() {
		new Thread(new sendToServer()).start();
		new Thread(new receiveFromServer()).start();
	}

	public void closeAll(){
		try { dis.close(); } catch (IOException e) {}
		try { dos.close(); } catch (IOException e) {}
		try { socket.close(); } catch (IOException e) {}
	}

	class sendToServer extends Thread {
		sendToServer() {
			try {
				dos = new DataOutputStream(socket.getOutputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void run() {
			Scanner scanner = new Scanner(System.in);
			long lastSendTime = System.currentTimeMillis();
			long sendInterval = 1000; // 흐름 제어를 위한 전송 간격 (예: 1초)

			while(dos != null){
				try {
					if (System.currentTimeMillis() - lastSendTime < sendInterval) {
						continue; // 네트워크 혼잡을 피하기 위해 일정 시간 간격을 둠
					}

					JSONObject jsonObject = new JSONObject();
					// 처음 입장한 사람. 로그인 안된 상태. 
					if(status == NOT_LOGGED_IN){
						System.out.print("채팅방 입장 아이디를 입력하세요 : ");
					}
					String input = scanner.nextLine();
					
					if(status == NOT_LOGGED_IN){
						// 중복인지 서버에게 확인해달라고 요청
						jsonObject.put("command", "incoming");
						jsonObject.put("data", input);
						dos.writeUTF(String.valueOf(jsonObject));
						dos.flush();
						id = input;
						status = LOGGING_IN;
					} else if(status == TEXT_TRANSMITTING) {
						if(input.startsWith("/file")){
							status = FILE_TRANSMITTING;
							fileInfo.put("fileOriginName", input.split(" ")[1].trim());
							System.out.print("파일을 전송하시겠습니까? (y/n) : ");
						} else {
							// [file]이 붙지 않으면 그냥 텍스트 전송
							jsonObject.put("command", "message");
							jsonObject.put("data", input);
							dos.writeUTF(jsonObject.toString());
						}
						dos.flush();
					} else if(status == FILE_TRANSMITTING) {
						// 파일을 전송한다면
						if(input.equals("y")){
							// FileUploader 클래스에 경로가 포함된 파일이름을 넘겨주고 스레드 시작
							FileUploader fileUploader = new FileUploader(Client.this, fileInfo.get("fileOriginName"));
							fileUploader.start();
						}
						// 텍스트 전송모드로 변경
						// 파일 정보는 초기화
						status = TEXT_TRANSMITTING;
						fileInfo.clear();
					} else if(status == FILE_DOWNLOADING) {
						// 파일을 다운로드한다면
						if(input.equals("y")){
							FileDownloader downloader = new FileDownloader(Client.this, fileInfo, true);
						} else {
							// FileDownloader 클래스에 파일정보(이름, uuid, 발신자) 넘겨주고 다운로드 하지 않음을 알림
							FileDownloader downloader = new FileDownloader(Client.this,fileInfo, false);
						}
						// 텍스트 전송모드로 변경
						// 파일 정보는 초기화
						status = TEXT_TRANSMITTING;
						fileInfo.clear();
					}
					lastSendTime = System.currentTimeMillis(); // 마지막 전송 시간 업데이트
				} catch(SocketException e){
					e.printStackTrace();
					System.out.println("서버와 연결이 끊어졌습니다.");
					closeAll();
					// break하지 않으면 SocketException가 무한으로 뜸
					break;
				} catch (Exception e) {
					closeAll();
				}
			}
		}

	}

	// InputStream
	class receiveFromServer extends Thread{

		receiveFromServer() {
			try {
				dis = new DataInputStream(socket.getInputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void run() {
			while(dis != null){
				try {
					String input = dis.readUTF();
					JSONObject inputJsonObject = new JSONObject(input);
					if(status == LOGGING_IN){
						// 로그인 대기 중인데 중복이 아니라면
						if(inputJsonObject.getString("data").equals("y")){
							// 로그인 됨. 메세지 보낼 수 있게 상태 변경
							status = TEXT_TRANSMITTING;
						} else {
							// 로그인 안 된 상태로 변경
							status = NOT_LOGGED_IN;
							id = "";
							System.out.print("채팅방 입장 아이디를 입력하세요 : ");
						}
					} else if(status==TEXT_TRANSMITTING || status==FILE_TRANSMITTING || status ==FILE_DOWNLOADING){
						if(inputJsonObject.getString("command").equals("download")){
							// 다운로드할 것인지 요청이 들어오면 다운로드 모드로 변경
							status = FILE_DOWNLOADING;
							fileInfo.put("uploader", inputJsonObject.getString("uploader"));
							fileInfo.put("fileOriginName", inputJsonObject.getString("fileOriginName"));
							fileInfo.put("fileUuidName", inputJsonObject.getString("fileUuidName"));
							// 다운로드 파일정보 및 발신자 안내
							System.out.print(fileInfo.get("uploader") + "가 보낸 " + fileInfo.get("fileOriginName") +" 파일을 받으시겠습니까? (y/n) : ");
						} else if(inputJsonObject.getString("command").equals("exit")){
							// 채팅 유저 퇴장 안내
							System.out.println(inputJsonObject.getString("data") + "님이 퇴장하셨습니다." );
						} else if(inputJsonObject.getString("command").equals("incoming")){
							// 채팅 유저 입장 안내
							System.out.println(inputJsonObject.getString("data") + "님이 입장하셨습니다.");
						} else if(inputJsonObject.getString("command").equals("message")){
							// 일반 채팅 텍스트
							System.out.println(inputJsonObject.getString("sender") + " : " + inputJsonObject.getString("data"));
						}
					}
				}catch(SocketException e){
					System.out.println("서버와 연결이 끊어졌습니다.");
					closeAll();
					break;
				} catch (Exception e) {
					e.printStackTrace();
					closeAll();
				}
			}
		}
	}



	public static void main(String[] args) {
		Client client = new Client();
	}
}