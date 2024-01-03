package chat.client;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;

import static chat.client.Client.*;

class FileUploader extends Thread {

    private Client client;
    private Socket fSocket;
    private DataOutputStream fDos;
    private FileInputStream fis;

    FileUploader(Client client, String filePath) throws Exception {
        this.client = client;
        // 파일경로 + 파일이름으로 File 객체 생성
        File file = new File(filePath);

        // 파일서버포트로 소켓연결
        this.fSocket = new Socket(SERVER_ADDRESS, FILE_SERVER_PORT);
        this.fDos = new DataOutputStream(fSocket.getOutputStream());
        this.fis = new FileInputStream(file);
        // 파일서버에 발신자와 파일이름 전송
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("command","upload");
        jsonObject.put("uploader", client.id);
        jsonObject.put("fileOriginName", file.getName());

        fDos.writeUTF(jsonObject.toString());
        fDos.flush();
    }

    @Override
    public void run() {
        try {    // 파일전송 시작
            byte[] buf = new byte[1024];
            int read = -1;
            // (recieveFromClient = fis.recieveFromClient(buf, 0, buf.length)) != -1로 하는 경우가 있는데 EOF Exception이 발생하기도 함
            while ((read = fis.read(buf, 0, buf.length)) > 0) {
                fDos.write(buf, 0, read);
            }
            fDos.flush();
            System.out.println("업로드를 완료했습니다.");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
            }
            try {
                if (fDos != null) {
                    fDos.close();
                }
            } catch (IOException e) {
            }
            try {
                if (fSocket != null) {
                    fSocket.close();
                }
            } catch (IOException e) {
            }
        }
    }
}
