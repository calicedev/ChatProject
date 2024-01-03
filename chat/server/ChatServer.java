package chat.server;

import org.json.JSONObject;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import static chat.client.Client.SERVER_PORT;

public class ChatServer {

	/*싱글톤, 규모가 작은 것에 적합함.
	각 사용자가 채팅을 위해 서버에 접속할 때
	인스턴스를 요청하는데 싱글톤이 아니라면 사용자 A는 서버 인스턴스 1에, 사용자 B는 서버 인스턴스 2에 연결된다.
	각각 다른 인스턴스라서 A가 B에게 보낸 메시지가 B에게 전달되지 않을 수 있다.
	synchronized 키워드를 통해 여러 스레드가 동시에 인스턴스 생성을 하는 것을 막는다.
	volatile을 통해서 인스턴스가 생성된 후 해당 인스턴스가 다른 스레드에게 즉시 보이도록 함.*/

	private volatile static ChatServer instance;
	public static ChatServer getInstance(){
		synchronized (ChatServer.class) {
			if(instance == null){
				instance = new ChatServer();
			}
		}
		return instance;
	}
	
	private ServerSocket server;
	private ArrayList<ChatServerManager> clients;
	public ChatServer(){
		try {
			// 8083번 포트에 서버소켓 생성
			this.server = new ServerSocket(SERVER_PORT);
			this.clients = new ArrayList<ChatServerManager>();
			System.out.println("[ChatServer] Starting");
		} catch (Exception e) {
			System.out.println("[Error] initializing ChatServer: " + e.getMessage());
		}
	}
	
	public void runServer(){
		while(true){
			try {
				// 클라이언트에게 연결 요청이 들어오면 수락한다.
				Socket socket = server.accept();
				System.out.println("접속 : " + socket.toString());
				// 서버 소켓이 소켓을 사용할 수 있도록 허락해준다, 생성 뒤, clients에 추가하고 해당 스레드를 start한다.
				ChatServerManager chatServerManager = new ChatServerManager(socket);
				clients.add(chatServerManager);
				chatServerManager.start();
			} catch (Exception e) {
				System.out.println("[Error] running ChatServer: " + e.getMessage());
			}
		}
	}

	// clients를 통해 아이디 중복체크
	public boolean existId(String userId){
		for(int i = 0, len = clients.size(); i<len; i++){
			if(userId.equals(clients.get(i).getUserId())){
				return true;
			}
		}
		return false;
	}

	// 사용자 아이디 리스트 반환
	public ChatServerManager getUser(String userId){
		ChatServerManager chatServerManager = null;
		for(ChatServerManager client : clients){
			if(client.getUserId().equals(userId)){
				chatServerManager = client;
				break;
			}
		}
		return chatServerManager;
	}
	
	// 사용자 아이디 리스트 반환
	public ArrayList<String> getUserList(){
		ArrayList<String> uList = new ArrayList<String>();
		for(ChatServerManager client : clients){
			uList.add(client.getUserId());
		}
		return uList;
	}

	// 모든 사용자에게 메세지 전달
	public void broadcast(JSONObject jsonObject){
		for(ChatServerManager client : clients){
			client.sendToClient(String.valueOf(jsonObject));
		}
	}

	// 업로더를 제외하고 나머지에게 다운로드 요청 정보를 보낸다.
	public void broadcastFile(String uploader, String fileOriginName, String fileUuidName){
		for(ChatServerManager client : clients){
			if(!client.getUserId().equals(uploader)){
				JSONObject jsonObject = new JSONObject();
				jsonObject.put("command","download");
				jsonObject.put("uploader",uploader);
				jsonObject.put("fileOriginName",fileOriginName);
				jsonObject.put("fileUuidName",fileUuidName);
				client.sendToClient(jsonObject.toString());
			}
		}
	}
	
	// 사용자 입장
	public void enterUser(String user){
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "incoming");
		jsonObject.put("data", user);
		for(ChatServerManager client : clients){
			client.sendToClient(jsonObject.toString());
		}
	}

	// 사용자 퇴장
	// 모든 사용자에게 나간 사용자 퇴장을 알리고 사용자 리스트, 파일전송 리스트에서 제거
	public void exitUser(ChatServerManager user){
		String exitedUser = user.getUserId();
		// 파일 리스트 내 해당 사용자 삭제
		FileServer.getInstance().removeExitedUser(exitedUser);
		// 사용자가 나갔음을 전달

		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "exit");
		jsonObject.put("data", exitedUser);

		for(int i = 0; i < clients.size(); i++){
			if(!clients.get(i).equals(exitedUser)){
				clients.get(i).sendToClient(jsonObject.toString());
			}
		}
		// 해당 사용자 리스트에서 삭제
		clients.remove(user);
	}

	public static void main(String[] args) {
		FileServer file = FileServer.getInstance();
		ChatServer chatServer = ChatServer.getInstance();

		file.start();
		chatServer.runServer();
	}

}
