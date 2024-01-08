package chat.server;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ChatServerManager extends Thread {

	private Socket socket;
	private DataInputStream dis;
	private DataOutputStream dos; 
	
	private String userId;
	
	// Serversocket으로 들어오는 접속을 감지되면 ChatServerManager 객체를 생성
	public ChatServerManager(Socket socket){
		try {
			this.socket = socket;
			this.dis = new DataInputStream(socket.getInputStream());
			this.dos = new DataOutputStream(socket.getOutputStream());
		} catch (Exception e) {
			closeAll();
		}
	}


	
	@Override
	public void run() {
		try {
			// 해당 사용자로부터의 응답을 받음
			while(dis != null){
				recieveFromClient();
			}
		} catch (Exception e) {
		} finally {
			closeAll();
			System.out.println("나감 : "+ socket.toString());
			ChatServer.getInstance().exitUser(this);
		}
	}

	public void recieveFromClient() throws Exception{
		String msg = dis.readUTF();
		JSONObject jsonObject = new JSONObject(msg);
		String command = jsonObject.getString("command");
		if("incoming".equals(command)){
			// userId가 없으면 중복체크 함.
			// 중복체크해서 중복 아이디이면 n
			// 중복 아니면 y
			// 있으면 n, 없으면 y를 보낸 후 다른 사용자들에게 새로운 사용자가 들어왔음을 알림
			if(userId == null){
				String id = jsonObject.getString("data");
				boolean isDuplicate = ChatServer.getInstance().existId(id);
				JSONObject sendJsonObject = new JSONObject();
				sendJsonObject.put("command", "incoming");
				sendJsonObject.put("data", isDuplicate ? "n" : "y");
				sendToClient(sendJsonObject.toString());
				if (!isDuplicate) {
					userId = id;
					ChatServer.getInstance().enterUser(userId);
				}
			} 
		} else if("message".equals(command)){
			// 텍스트 전송이 들어오면 모든 사용자에게 사용자의 메세지를 전송
			jsonObject.put("sender",userId);
			ChatServer.getInstance().broadcast(jsonObject);
		}
	}
	
	// 사용자에게 메세지를 전달, 주로 Server에서 호출
	public void sendToClient(String msg){
		try {
			dos.writeUTF(msg);
			dos.flush();
		} catch (Exception e) {
			closeAll();
		}
	}

	// 사용자의 아이디를 반환
	public String getUserId() {
		return userId;
	}
	public void closeAll(){
		try {
			if(dos != null) {
				dos.close();
			}
		} catch (IOException e) {
		}
		try {
			if(dis != null) {
				dis.close();
			}
		} catch (IOException e) {
		}
		try {
			if(socket != null) {
				socket.close();
			}
		} catch (IOException e) {
		}
	}
}
