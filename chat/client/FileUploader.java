package chat.client;

import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.util.UUID;

import static chat.client.Client.*;

class FileUploader implements Runnable {

    private Client client;
    private Socket fSocket;
    private DataOutputStream fDos;
    private DataInputStream fDis;
    private FileInputStream fis;

    private File file;
    FileUploader(Client client, String filePath) throws Exception {
        this.client = client;
        // 파일경로 + 파일이름으로 File 객체 생성
        this.file = new File(filePath);

        // 파일서버포트로 소켓연결
        this.fSocket = new Socket(SERVER_ADDRESS, FILE_SERVER_PORT);
        this.fDos = new DataOutputStream(fSocket.getOutputStream());
        this.fis = new FileInputStream(file);
        this.fDis = new DataInputStream(fSocket.getInputStream());
        // 파일서버에 발신자와 파일이름 전송
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("command","upload");
        jsonObject.put("uploader", client.id);
        jsonObject.put("fileOriginName", file.getName());
        jsonObject.put("fileSize", file.length());
        fDos.writeUTF(jsonObject.toString());
        fDos.flush();
    }

    @Override
    public void run() {
        try {
            byte[] buffer = new byte[1024]; // 패킷 크기 설정
            int read;
            int packetNumber = 0;
            long fileSize = file.length();
            String fileId = UUID.randomUUID().toString();

            while ((read = fis.read(buffer)) > 0) {
                JSONObject header = new JSONObject();
                header.put("packetNumber", ++packetNumber); // 패킷 번호를 1증가 시키고, 헤더에 추가
                header.put("bytes", read); // 현재 패킷에서 읽은 바이트 수
                header.put("fileId", fileId); // 파일 식별자
                header.put("fileSize", fileSize); // 파일 전체 사이즈
                header.put("packetType", packetNumber == 1 ? "start" : "middle"); // 첫번째 패킷이라면 start, 아니면 middle

//                header.put("checksum", calculateChecksum(buffer, read));
                fDos.writeUTF(header.toString()); // 헤더 전송
                fDos.write(buffer, 0, read); // 데이터 전송
                fDos.flush();
//                System.out.println("Packet " + packetNumber + " sent, size: " + read + " bytes");

                // 패킷 하나 전송하고 서버로 부터 응답을 읽어들인다.
                // 응답이 Error이면 해당 패킷 번호부터 재전송 로직.
                String response = fDis.readUTF();
                if ("ERROR".equals(response)) {
                    System.out.println("Packet " + packetNumber + " transmission failed. Retrying...");
                    // 재전송 로직 추가
                    while (true) {
                        // 패킷 재전송
                        fDos.writeUTF(header.toString()); // 헤더 전송
                        fDos.write(buffer, 0, read); // 데이터 전송
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
            }
            // 파일 끝을 나타내는 신호
            fDos.writeUTF("END_OF_FILE");
            fDos.flush();
            System.out.println("업로드를 완료했습니다.");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 자원 정리
            try {
                if (fis != null){
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                if (fDos != null) {
                    fDos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                if (fSocket != null) {
                    fSocket.close();}
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
