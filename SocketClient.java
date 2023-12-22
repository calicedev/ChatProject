package ch19.sec07;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.json.JSONObject;

public class SocketClient {
	//필드
	ChatServer chatServer;
	Socket socket;
	DataInputStream dis;
	DataOutputStream dos;
	String clientIp;	
	String chatName;
	//생성자
	public SocketClient(ChatServer chatServer, Socket socket) {
		try {
			this.chatServer = chatServer;
			this.socket = socket;
			this.dis = new DataInputStream(socket.getInputStream());
			this.dos = new DataOutputStream(socket.getOutputStream());
			InetSocketAddress isa = (InetSocketAddress) socket.getRemoteSocketAddress();
			this.clientIp = isa.getHostName();			
			receiveFromClient();
		} catch(IOException e) {
		}
	}	
	//메소드: JSON 받기
	public void receiveFromClient() {
		chatServer.threadPool.execute(() -> {
			try {
				while(true) {
					String receiveJson = dis.readUTF();		
					
					JSONObject jsonObject = new JSONObject(receiveJson);
					String command = jsonObject.getString("command");
					
					switch(command) {
						case "incoming":
							this.chatName = jsonObject.getString("data");
							chatServer.sendToAllClient(this, "들어오셨습니다.");
							chatServer.addSocketClient(this);
							break;
						case "message":
							String message = jsonObject.getString("data");
							chatServer.sendToAllClient(this, message);
							break;
					}
				}
			} catch(IOException e) {
				chatServer.sendToAllClient(this, "나가셨습니다.");
				chatServer.removeSocketClient(this);
			}
		});
	}
	//메소드: JSON 보내기
	public void sendToClient(String json) {
		try {
			dos.writeUTF(json);
			dos.flush();
		} catch(IOException e) {
		}
	}	
	//메소드: 연결 종료
	public void closeSocket() {
		try { 
			socket.close();
		} catch(Exception e) {}
	}
}