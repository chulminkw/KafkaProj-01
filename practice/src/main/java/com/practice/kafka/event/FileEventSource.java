package com.practice.kafka.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ExecutionException;

// 파일에 내용이 추가되었는지 Thread로 모니터링하면서 내용 추가된 경우 EventHandler를 호출하여 Producer로 해당 메시지 전송
// Thread로 수행되므로 Runnable Interface 구현.
public class FileEventSource implements Runnable {
    public static final Logger logger = LoggerFactory.getLogger(FileEventSource.class.getName());

    //주기적으로 file을 모니터할 기간. ms 단위.
    private long updateInterval;

    //모니터할 File 객체
    private File file;

    //file에 변경사항이 발생했을 때 Producer를 이용하여 메시지를 전송하는 EventHandler
    private EventHandler eventHandler;
    private boolean sync;

    private long filePointer = 0;
    public boolean keepRunning = true;

    public FileEventSource(long updateInterval, File file, EventHandler eventHandler) {
        this.updateInterval = updateInterval;
        this.file = file;
        this.eventHandler = eventHandler;
    }

    //무한 반복 수행하되, updateInterval 동안 sleep하면서 파일에 내용이 추가되었는지 모니터링 후 메시지 전송.
    @Override
    public void run() {
        try {
            //this.keepRunning은 Main Thread가 종료시 false로 변경 되도록 Main 프로그램 수정.
            while(this.keepRunning) {
                //생성자에 입력된 updateInterval ms 동안 sleep
                Thread.sleep(this.updateInterval);
                //file의 크기를 계산.
                long len = this.file.length();
                //만약 최종 filePointer가 현재 file의 length보다 작다면 file이 초기화 되었음.
                if (len < this.filePointer) {
                    logger.info("log file was reset as filePointer is longer than file length");
                    //최종 filePointer를 file의 length로 설정.
                    filePointer = len;
                    // 만약 file의 length가 최종 filePointer보다 크다면 file이 append 되었음.
                } else if (len > this.filePointer){
                    //최종 filePointer에서 맨 마지막 파일까지 한 라인씩 읽고 이를 Producer에서 메시지로 전송.
                    readAppendAndSend();
                    // 만약 file의 length와 filePointer가 같다면 file에 변화가 없음.
                } else {
                    continue;
                }
            }
        } catch(InterruptedException e) {
            logger.error(e.getMessage());
        } catch(ExecutionException e) {
            logger.error(e.getMessage());
        } catch(Exception e) {
            logger.error(e.getMessage());
        }

    }

    //최종 filePointer에서 맨 마지막 파일까지 한 라인씩 읽고 sendMessage()를 이용하여 메시지로 전송
    public void readAppendAndSend() throws IOException, ExecutionException, InterruptedException {
        RandomAccessFile raf = new RandomAccessFile(this.file, "r");
        raf.seek(this.filePointer);
        String line = null;

        while((line = raf.readLine()) != null) {

            sendMessage(line);
        }
        //file이 변경되었으므로  file의 filePointer를 현재 file의 마지막으로 재 설정함.
        this.filePointer = raf.getFilePointer();
    }

    //한 라인을 parsing하여 key와 value로 MessageEvent를 만들고 이를 EventHandler를 이용하여 Producer 전송.
    private void sendMessage(String line) throws ExecutionException, InterruptedException {
        String[] tokens = line.split(",");
        String key = tokens[0];
        StringBuffer value = new StringBuffer();

        for(int i=1; i<tokens.length; i++) {
            if( i != (tokens.length -1)) {
                value.append(tokens[i] + ",");
            } else {
                value.append(tokens[i]);
            }
        }
        MessageEvent messageEvent = new MessageEvent(key, value.toString());
        eventHandler.onMessage(messageEvent);
    }

}