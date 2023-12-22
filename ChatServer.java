package ch19.sec07;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

public class ChatServer {

	// chatServer은 처음 서버를 시작하는 파일이다.
	// 이 파일은 메인스레드 1개, 서버 스레드 한개 생성됨.

	//필드
	//서버 측에서 클라이언트의 연결 요청을 수신하기 위해 사용되는 것.
	// 특정 포트에서 들어오는 연결 요청을 기다리고 있음.
	ServerSocket serverSocket;
	ExecutorService threadPool = Executors.newFixedThreadPool(100);
	Map<String, SocketClient> chatRoom = Collections.synchronizedMap(new HashMap<>());

	//메소드: 서버 시작
	public void startServer() throws IOException {
		serverSocket = new ServerSocket(50001);	
		System.out.println( "[서버] 시작됨");

		while (true) {
			try {
				System.out.println("[서버] 클라이언트 연결 대기 중");
				// 클라이언트의 연결이 들어오기 전까지 블로킹.
				// 클라이언트가 연결을 시도하면, accept() 메서드는 클라이언트와의 통신을 위한 새로운 Socket 객체를 반환.
				Socket socket = serverSocket.accept();

				// 스레드 풀에 작업 제출
				threadPool.submit(() -> {
					// 소켓 클라이언트 생성.
					SocketClient sc = new SocketClient(this, socket);
				});

			} catch (IOException e) {
				if (serverSocket.isClosed()) {
					break;
				}
				e.printStackTrace();
			}
		}


		/* 기존 코드
		Thread thread = new Thread(() -> {
			try {
				while(true) {
					System.out.println("[서버] 서버 스레드 하나 생김");
					Socket socket = serverSocket.accept();
					SocketClient sc = new SocketClient(this, socket);
				}
			} catch(IOException e) {
			}
		});
		thread.start();*/
	}

	//메소드: 클라이언트 연결시 SocketClient 생성 및 추가
	public void addSocketClient(SocketClient socketClient) {
		String key = socketClient.chatName + "@" + socketClient.clientIp;
		chatRoom.put(key, socketClient);
		System.out.println("입장: " + key);
		System.out.println("현재 채팅자 수: " + chatRoom.size() + "\n");
	}

	//메소드: 클라이언트 연결 종료시 SocketClient 제거
	public void removeSocketClient(SocketClient socketClient) {
		String key = socketClient.chatName + "@" + socketClient.clientIp;
		chatRoom.remove(key);
		System.out.println("나감: " + key);
		System.out.println("현재 채팅자 수: " + chatRoom.size() + "\n");
	}		
	//메소드: 모든 클라이언트에게 메시지 보냄
	public void sendToAllClient(SocketClient sender, String message) {
		JSONObject root = new JSONObject();
		root.put("clientIp", sender.clientIp);
		root.put("chatName", sender.chatName);
		root.put("message", message);
		String json = root.toString();
		
		Collection<SocketClient> socketClients = chatRoom.values();
		for(SocketClient sc : socketClients) {
			if(sc == sender) continue;
			sc.sendToClient(json);
		}
	}	
	//메소드: 서버 종료
	public void stopServer() {
		try {
			serverSocket.close();
			threadPool.shutdownNow();
			chatRoom.values().stream().forEach(sc -> sc.closeSocket());
			System.out.println( "[서버] 종료됨 ");
		} catch (IOException e1) {}
	}		
	//메소드: 메인
	public static void main(String[] args) {	
		try {
			ChatServer chatServer = new ChatServer();
			chatServer.startServer();
			
			System.out.println("----------------------------------------------------");
			System.out.println("서버를 종료하려면 q를 입력하고 Enter.");
			System.out.println("----------------------------------------------------");
			
			Scanner scanner = new Scanner(System.in);
			while(true) {
				String key = scanner.nextLine();
				if(key.equals("q")) 	break;
			}
			scanner.close();
			chatServer.stopServer();
		} catch(IOException e) {
			System.out.println("[서버] " + e.getMessage());
		}
	}
}