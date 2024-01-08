package chat.client;

import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;

import static chat.client.Client.*;

class FileDownloader implements Runnable {

    private Client client;
    private HashMap<String, String> fileInfo;
    private Socket fSocket;
    private DataInputStream fDis;
    private DataOutputStream fDos;
    private FileOutputStream fos;

    private String FILE_PATH = "C:\\new\\";

    FileDownloader(Client client, HashMap<String, String> fileInfo) {
        this.client = client;
        this.fileInfo = fileInfo;
        try {
            this.fSocket = new Socket(SERVER_ADDRESS, FILE_SERVER_PORT);
            this.fDis = new DataInputStream(fSocket.getInputStream());
            this.fDos = new DataOutputStream(fSocket.getOutputStream());

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

    }

    @Override
    public void run() {
        try {
            // 파일을 저장할 객체 생성

                byte[] buf = new byte[1024];
                int read;
                int lastPacketNumber = 0;
                long fileSize = 0;
                String fileId = null;
                String packetType = null;
                while (true) {
                    // 서버로부터 헤더 정보(패킷 번호와 바이트 수)를 읽음
                    String headerJson = fDis.readUTF();
                    if ("END_OF_FILE".equals(headerJson)) {
                        System.out.println("다운로드를 완료했습니다.");
                        break; // 파일 다운로드 완료
                    }

                    JSONObject header = new JSONObject(headerJson);
                    int packetNumber = header.getInt("packetNumber");
                    int bytes = header.getInt("bytes");

                    if (packetNumber != lastPacketNumber + 1) {
                        fDos.writeUTF("ERROR");
                        fDos.flush();
                        System.out.println("Packet " + packetNumber + " transmission failed. Retrying...");
                        // 재전송 로직 추가
                        while (true) {
                            // 패킷 재전송
                            String retransmitHeaderJson = fDis.readUTF();
                            fos.write(retransmitHeaderJson.getBytes("UTF-8"));
                            byte[] retransmitData = new byte[bytes];
                            fDis.read(retransmitData, 0, bytes);
                            fos.write(retransmitData, 0, bytes);

                            // 서버로부터 ACK 또는 ERROR 수신 대기
                            String response = fDis.readUTF();
                            if ("ACK".equals(response)) {
                                System.out.println("Packet " + packetNumber + " retransmitted successfully.");
                                break;
                            } else {
                                System.out.println("Packet " + packetNumber + " retransmission failed. Retrying...");
                            }
                        }
                    }else{ // 패킷을 정상적으로 받았다는 ACK 전송
                        fDos.writeUTF("ACK");
                        fDos.flush();
                    }

                    if (packetNumber == 1) {
                        fileId = header.getString("fileId");
                        fileSize = header.getLong("fileSize");
                        packetType = "start";
                    } else {
                        packetType = "middle";
                    }

                    // 지정된 바이트 수만큼 데이터를 읽고 파일에 기록
                    read = fDis.read(buf, 0, bytes);
                    fos.write(buf, 0, read);
                    lastPacketNumber = packetNumber;

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
