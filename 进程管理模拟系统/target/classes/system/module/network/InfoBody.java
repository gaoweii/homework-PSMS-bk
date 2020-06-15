package system.module.network;


import lombok.Data;

@Data
public class InfoBody {
    private String header;
    private String msg; //这里根据需要选择是否反序列化为对象

    public InfoBody() {

    }

    public InfoBody(String header, String msg) {
        this.header = header;
        this.msg = msg;
    }

    public InfoBody(String header) {
        this.header = header;
    }

    public void cleanBody() {
        this.header = "";
        this.msg = "";
    }

}

enum HeaderInfo {
    ERROR, LOG, PCB, PROCESSPOOL

}
