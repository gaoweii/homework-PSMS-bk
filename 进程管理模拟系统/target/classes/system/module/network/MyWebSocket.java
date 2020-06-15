package system.module.network;


import com.alibaba.fastjson.JSON;
import lombok.SneakyThrows;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import system.annotation.Port;
import system.module.FrameworkMsg;
import system.module.core.Module;
import system.module.core.ProcessFramework;
import system.module.processpool.PCB;
import system.module.processpool.ProcessPool;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;


/**这里的run接口WebSocketServer已经实现了*/
/**信息量大的时候，考虑把信息处理部分抽离出来，单独成为一个服务，WebSocket部分和信息处理部分通过消息队列进行交互，提高系统吞吐率,消息队列必须是线程安全的*/
@Port(7000)
public class MyWebSocket extends WebSocketServer implements Module {

    private static Logger logger = LoggerFactory.getLogger("log");

    private FrameworkMsg msg;

    private FileInputStream inputStream;

    private ByteArrayOutputStream byteArrayOutputStream;

    private InfoBody requestBody;

    private InfoBody responseBody;

    private WebSocket webSocket;

    public void send(InfoBody infoBody) {
        send(webSocket, infoBody);
    }


    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        logger.info(webSocket.getRemoteSocketAddress().toString() + " has connected...");
        this.webSocket = webSocket;

    }

    public void sendPCBList(WebSocket webSocket) {
        List<PCB> pcbList = ProcessFramework.getPCBList();
        responseBody.setHeader("PCB");
        responseBody.setMsg(JSON.toJSONString(pcbList));
        send(webSocket, responseBody);
    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        logger.info(webSocket.getRemoteSocketAddress().getAddress().toString() + " closed the socket.");
        close();
    }

    private void send(WebSocket webSocket, InfoBody responseBody) {
        webSocket.send(JSON.toJSONString(responseBody));
        responseBody.cleanBody();
    }

    private void processPool_FIFO() {
        Thread thread = ProcessFramework.getThreadList().get("system.module.processpool.ProcessPool");
        msg.sendMsgToThread(thread, FrameworkMsg.Instructions.FIFO);
    }

    private void processPool_QUEUE_SHOW(WebSocket webSocket) {
        ProcessPool processPool = (ProcessPool) ProcessFramework.getAllService().get("system.module.processpool.ProcessPool");
        List<List<PCB>> queues = new ArrayList<>();
        queues.add(processPool.getWaitQueue());
        queues.add(processPool.getReadyQueue());
        queues.add(processPool.getRunQueue());
        responseBody.setMsg(JSON.toJSONString(queues));
        responseBody.setHeader("queues");
        send(webSocket, responseBody);
    }

    public void processPool(WebSocket webSocket, String instructions) {
        try {
            FrameworkMsg.Instructions instructions1 = FrameworkMsg.Instructions.valueOf(instructions.toUpperCase());
            switch (instructions1) {
                case FIFO:
                    processPool_FIFO();
                    break;
                case SHUTDOWN_PROCESSPOOL:
                    break;
                case QUEUE_SHOW:
                    processPool_QUEUE_SHOW(webSocket);
                    break;
                case SJF:
                    processPool_SJF();
                    break;
                case RR:
                    processPool_RR();
                    break;
                default:break;
            }
        } catch (Exception e) {
            sendError(webSocket, "illegal ProcessPool instruction: " + instructions);
        }
    }

    private void processPool_RR() {
        Thread thread = ProcessFramework.getThreadList().get("system.module.processpool.ProcessPool");
        msg.sendMsgToThread(thread, FrameworkMsg.Instructions.RR);
    }

    private void processPool_SJF() {
        Thread thread = ProcessFramework.getThreadList().get("system.module.processpool.ProcessPool");
        msg.sendMsgToThread(thread, FrameworkMsg.Instructions.SJF);
    }


    public void infoProcessing(WebSocket webSocket, String s) {
        requestBody = JSON.parseObject(s, InfoBody.class);
        try {
            if (requestBody.getHeader() == "" || requestBody == null) {
                throw new Exception();
            }
            HeaderInfo headerInfo = HeaderInfo.valueOf(requestBody.getHeader().toUpperCase());
            switch (headerInfo) {
                case LOG:
                    sendLog(webSocket);
                    break;
                case PCB:
                    sendPCBList(webSocket);
                    break;
                case PROCESSPOOL:
                    processPool(webSocket, requestBody.getMsg());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            sendError(webSocket, "illegal header: " + requestBody.getHeader());
        }

    }

    private void sendError(WebSocket webSocket, String errorInfo) {
        responseBody.setHeader("systemError");
        responseBody.setMsg(errorInfo);
        System.out.println(errorInfo);
        send(webSocket, responseBody);
    }

    private void sendError(WebSocket webSocket, Exception e) {
        responseBody.setHeader("systemError");

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(e.toString());
        stringBuilder.append("\n");
        StackTraceElement[] stackTraceElements = e.getStackTrace();
        for (StackTraceElement stackTraceElement : stackTraceElements) {
            stringBuilder.append("\tat ");
            stringBuilder.append(stackTraceElement.toString());
            stringBuilder.append("\n");
        }
        responseBody.setMsg(stringBuilder.toString());
        send(webSocket, responseBody);
    }

    @SneakyThrows
    public void sendLog(WebSocket webSocket) {
        int readSize = 0;
        int totalSize = 0;
        byte[] bytes = new byte[2048];
        while ((readSize = inputStream.read(bytes)) > 0) {
            totalSize += readSize;
            byteArrayOutputStream.write(bytes, 0, readSize);
        }
        responseBody.setHeader("log");
        responseBody.setMsg(byteArrayOutputStream.toString());
        send(webSocket, responseBody);
    }

    @SneakyThrows
    @Override
    public void onMessage(WebSocket webSocket, String s) {
       infoProcessing(webSocket, s);
    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {
        logger.error("error", e);
        sendError(webSocket, e);
    }

    @SneakyThrows
    @Override
    public void onStart() {
        logger.info("wait client...");

    }


    @SneakyThrows
    @Override
    public void init() {
        inputStream = new FileInputStream("src\\main\\resources\\logs\\log.log");
        byteArrayOutputStream = new ByteArrayOutputStream();
        requestBody = new InfoBody();
        responseBody = new InfoBody();
        msg = ProcessFramework.getFrameworkMsg();
        logger.info("WebSocket init port = " + getPort());

    }

    @Override
    public void close() {

    }

    public MyWebSocket(InetSocketAddress inetSocketAddress) {
        super(inetSocketAddress);
    }
}
