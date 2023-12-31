package chat.client;

import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;

import static chat.client.Client.*;

class FileDownloader extends Thread {

    private Client client;
    private HashMap<String, String> fileInfo;
    private Socket fSocket;
    private DataInputStream fDis;
    private DataOutputStream fDos;
    private FileOutputStream fos;

    private String FILE_PATH = "C:\\new\\";

    FileDownloader(Client client, HashMap<String, String> fileInfo, boolean isDownload) {
        this.client = client;
        this.fileInfo = fileInfo;
        try {
            this.fSocket = new Socket(SERVER_ADDRESS, FILE_SERVER_PORT);
            this.fDis = new DataInputStream(fSocket.getInputStream());
            this.fDos = new DataOutputStream(fSocket.getOutputStream());
            System.out.println("파일 서버에 연결되었습니다.");
            // 받는다면
            if (isDownload) {
                yesDownload();
                // 스레드 실행
                this.start();
            } else {
                // 받지 않는다면
                noDownload();
            }
        } catch (SocketException e) {
            System.out.println("서버와 연결이 끊어졌습니다.");
            closeAll();
        } catch (Exception e) {
            e.printStackTrace();
            closeAll();
        }
    }

    // 서버에 저장되어있는 파일을 요청함
    public void yesDownload() throws Exception {
        // 서버에서 받은 파일이름으로 File객체 생성
        File file = new File(FILE_PATH + client.fileInfo.get("fileOriginName"));
        fos = new FileOutputStream(file);
        System.out.println("다운로드를 시작합니다: " + file.getName());
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("command","download");
        jsonObject.put("downloader",client.id);
        jsonObject.put("fileUuidName",fileInfo.get("fileUuidName"));
        jsonObject.put("data","y");
        fDos.writeUTF(jsonObject.toString());
        fDos.flush();
    }

    // 파일을 받지 않음
    public void noDownload() throws Exception {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("command","download");
        jsonObject.put("downloader",client.id);
        jsonObject.put("fileUuidName",fileInfo.get("fileUuidName"));
        jsonObject.put("data","n");
        fDos.writeUTF(jsonObject.toString());
        fDos.flush();
        System.out.println("다운로드 요청이 거부되었습니다.");

    }

    @Override
    public void run() {
        try {
            // 파일을 저장할 객체 생성

                byte[] buf = new byte[1024];
                int read;

                while (true) {
                    // 서버로부터 헤더 정보(패킷 번호와 바이트 수)를 읽음
                    String headerJson = fDis.readUTF();
                    if ("END_OF_FILE".equals(headerJson)) {
                        System.out.println("다운로드를 완료했습니다.");
                        break; // 파일 다운로드 완료
                    }

                    JSONObject header = new JSONObject(headerJson);
                    int bytes = header.getInt("bytes");
                    System.out.println("패킷 받음: 바이트 수 = " + bytes);
                    // 지정된 바이트 수만큼 데이터를 읽고 파일에 기록
                    read = fDis.read(buf, 0, bytes);
                    fos.write(buf, 0, read);

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }
    }


    public void closeAll() {
        try {
            if (fos != null) {
                fos.close();
            }
        } catch (IOException e) {
        }
        try {
            if (fDis != null) {
                fDis.close();
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
