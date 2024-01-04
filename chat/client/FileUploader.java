package chat.client;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.UUID;

import static chat.client.Client.*;

class FileUploader extends Thread {

    private Client client;
    private Socket fSocket;
    private DataOutputStream fDos;
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
            String fileId = UUID.randomUUID().toString(); // 파일 식별자

            while ((read = fis.read(buffer)) > 0) {
                JSONObject header = new JSONObject();
                header.put("packetNumber", ++packetNumber);
                header.put("bytes", read);
//                header.put("checksum", calculateChecksum(buffer, read));
                header.put("fileId", fileId);
                header.put("fileSize", fileSize);
                header.put("packetType", packetNumber == 1 ? "start" : "middle");

                fDos.writeUTF(header.toString()); // 헤더 전송
                fDos.write(buffer, 0, read); // 데이터 전송
                fDos.flush();

//                System.out.println("Packet " + packetNumber + " sent, size: " + read + " bytes");
            }
            fDos.writeUTF("END_OF_FILE");
            fDos.flush();
            System.out.println("업로드를 완료했습니다.");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 자원 정리
            try { if (fis != null) fis.close(); } catch (IOException e) { e.printStackTrace(); }
            try { if (fDos != null) fDos.close(); } catch (IOException e) { e.printStackTrace(); }
            try { if (fSocket != null) fSocket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }
}
