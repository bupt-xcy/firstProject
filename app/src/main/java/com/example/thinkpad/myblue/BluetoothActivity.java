package com.example.thinkpad.myblue;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by ThinkPad on 2016/3/22.
 */
public class BluetoothActivity extends Activity {

    /* 一些常量，代表服务器的名称 */
    public static final String PROTOCOL_SCHEME_RFCOMM = "btspp";

    private ListView mListView;
    private Button sendButton;
    private Button disconnectButton;
    private EditText editMsgView;
    private ArrayAdapter<String> mAdapter;
    private List<String> msgList=new ArrayList<String>();
    Context mContext;

    private BluetoothServerSocket mserverSocket = null;
//    private ServerThread startServerThread = null;
    private clientThread clientConnectThread = null;
    private BluetoothSocket socket = null;
    private BluetoothDevice device = null;
    private readThread mreadThread = null;;
    private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    /** 缓存 */
    private byte[] bffer = new byte[2];

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat);
        mContext = this;
        init();
    }

    private void init() {

        mAdapter=new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, msgList);
        mListView = (ListView) findViewById(R.id.list);
        mListView.setAdapter(mAdapter);
        mListView.setFastScrollEnabled(true);
        editMsgView= (EditText)findViewById(R.id.MessageText);
        editMsgView.clearFocus();

        sendButton= (Button)findViewById(R.id.btn_msg_send);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {

                String msgText =editMsgView.getText().toString();
                if (msgText.length()>0) {
//                    sendMessageHandle(msgText);
                    editMsgView.setText("");
                    editMsgView.clearFocus();
                    //close InputMethodManager
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(editMsgView.getWindowToken(), 0);
                }else
                    Toast.makeText(mContext, "发送内容不能为空！", Toast.LENGTH_SHORT).show();
            }
        });

        disconnectButton= (Button)findViewById(R.id.btn_disconnect);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                // TODO Auto-generated method stub
                if (BluetoothMsg.serviceOrCilent == BluetoothMsg.ServerOrCilent.CILENT)
                {
                    shutdownClient();
                }
                else if (BluetoothMsg.serviceOrCilent == BluetoothMsg.ServerOrCilent.SERVICE)
                {
                    shutdownServer();
                }
                BluetoothMsg.isOpen = false;
                BluetoothMsg.serviceOrCilent=BluetoothMsg.ServerOrCilent.NONE;
                Toast.makeText(mContext, "已断开连接！", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Handler LinkDetectedHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            //Toast.makeText(mContext, (String)msg.obj, Toast.LENGTH_SHORT).show();
            if(msg.what==1)
            {
                msgList.add((String)msg.obj);
            }
            else
            {
                msgList.add((String)msg.obj);
            }
            mAdapter.notifyDataSetChanged();
            mListView.setSelection(msgList.size() - 1);
        }
    };

    @Override
    protected void onResume() {

        BluetoothMsg.serviceOrCilent=BluetoothMsg.ServerOrCilent.CILENT;

        if(BluetoothMsg.isOpen)
        {
            Toast.makeText(mContext, "连接已经打开，可以通信。如果要再建立连接，请先断开！", Toast.LENGTH_SHORT).show();
            return;
        }
        if(BluetoothMsg.serviceOrCilent==BluetoothMsg.ServerOrCilent.CILENT)
        {
            String address = BluetoothMsg.BlueToothAddress;
            if(!address.equals("null"))
            {
                device = mBluetoothAdapter.getRemoteDevice(address);
                clientConnectThread = new clientThread();
                clientConnectThread.start();
                BluetoothMsg.isOpen = true;
            }
            else
            {
                Toast.makeText(mContext, "address is null !", Toast.LENGTH_SHORT).show();
            }
        }
        else if(BluetoothMsg.serviceOrCilent==BluetoothMsg.ServerOrCilent.SERVICE)
        {
//            startServerThread = new ServerThread();
//            startServerThread.start();
            BluetoothMsg.isOpen = true;
        }
        super.onResume();
    }

    //开启客户端
    private class clientThread extends Thread {
        @Override
        public void run() {
            try {
                //创建一个Socket连接：只需要服务器在注册时的UUID号
                // socket = device.createRfcommSocketToServiceRecord(BluetoothProtocols.OBEX_OBJECT_PUSH_PROTOCOL_UUID);
                socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                //连接
                Message msg2 = new Message();
                msg2.obj = "请稍候，正在连接服务器:"+BluetoothMsg.BlueToothAddress;
                msg2.what = 0;
                LinkDetectedHandler.sendMessage(msg2);

                socket.connect();

                Message msg = new Message();
                msg.obj = "已经连接上服务端！可以发送信息。";
                msg.what = 0;
                LinkDetectedHandler.sendMessage(msg);
                sendMessageHandle();
                //启动接受数据
                mreadThread = new readThread();
                mreadThread.start();
            }
            catch (IOException e)
            {
                Log.e("connect", "", e);
                Message msg = new Message();
                msg.obj = "连接服务端异常！断开连接重新试一试。";
                msg.what = 0;
                LinkDetectedHandler.sendMessage(msg);
            }
        }
    };

    //开启服务器
    private class ServerThread extends Thread {
        @Override
        public void run() {

            try {
                    /* 创建一个蓝牙服务器
                     * 参数分别：服务器名称、UUID   */
                mserverSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(PROTOCOL_SCHEME_RFCOMM,
                        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));

                Log.d("server", "wait cilent connect...");

                Message msg = new Message();
                msg.obj = "请稍候，正在等待客户端的连接...";
                msg.what = 0;
                LinkDetectedHandler.sendMessage(msg);

                    /* 接受客户端的连接请求 */
                socket = mserverSocket.accept();
                Log.d("server", "accept success !");

                Message msg2 = new Message();
                String info = "客户端已经连接上！可以发送信息。";
                msg2.obj = info;
                msg.what = 0;
                LinkDetectedHandler.sendMessage(msg2);
                //启动接受数据
                mreadThread = new readThread();
                mreadThread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
    /* 停止服务器 */
    private void shutdownServer() {
        new Thread() {
            @Override
            public void run() {
//                if(startServerThread != null)
//                {
//                    startServerThread.interrupt();
//                    startServerThread = null;
//                }
                if(mreadThread != null)
                {
                    mreadThread.interrupt();
                    mreadThread = null;
                }
                try {
                    if(socket != null)
                    {
                        socket.close();
                        socket = null;
                    }
                    if (mserverSocket != null)
                    {
                        mserverSocket.close();/* 关闭服务器 */
                        mserverSocket = null;
                    }
                } catch (IOException e) {
                    Log.e("server", "mserverSocket.close()", e);
                }
            };
        }.start();
    }
    /* 停止客户端连接 */
    private void shutdownClient() {
        new Thread() {
            @Override
            public void run() {
                if(clientConnectThread!=null)
                {
                    clientConnectThread.interrupt();
                    clientConnectThread= null;
                }
                if(mreadThread != null)
                {
                    mreadThread.interrupt();
                    mreadThread = null;
                }
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    socket = null;
                }
            };
        }.start();
    }

    //发送数据
    private void sendMessageHandle()
    {
        if (socket == null)
        {
            Toast.makeText(mContext, "没有连接", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            OutputStream os = socket.getOutputStream();
//            os.write(msg.getBytes());
            os.write(setByte());
        } catch (IOException e) {
            e.printStackTrace();
        }
//        msgList.add(msg);
        mAdapter.notifyDataSetChanged();
        mListView.setSelection(msgList.size() - 1);
    }
    //读取数据
    private class readThread extends Thread {


        private void resetMemey(byte[] by) {
            for (int i = 0; i < by.length; i++) {
                by[i] = (byte) 0xff;
            }
        }

        /**
         * 从输出流中读取指定长度的数据
         *
         * @param is
         * @param buffer
         *            缓存
         * @param offset
         *            缓存的 开始位置
         * @param length
         *            长度
         * @return
         * @throws IOException
         */
        private int fill(InputStream is, byte[] buffer, int offset, int length)
                throws IOException {
            int sum = 0, len;
            while (sum < length) {
                len = is.read(buffer, offset + sum, length - sum);
                if (len < 0) {
                    throw new IOException("End of stream");
                } else
                    sum += len;
            }
            return sum;
        }

        /**
         * 找数据包头
         *
         * @param inpus
         * @return
         * @throws IOException
         */
        private int checkHead(InputStream inpus) throws IOException {
            int len = -1;
            inpus.read(bffer, 0, 2);
            if (bffer[0] >= 0 && bffer[0] <= 13) {
                byte b = bffer[1];
                if (b < 0) {
                    len = (b + 256);
                }
                if (len > 200) {
                    len = -1;
                } else {
                    len = b;
                }
            }

            if (len == -1)
                bffer[0] = bffer[1];
            return len;
        }

        @Override
        public void run() {

            InputStream mmInStream = null;

            try {
                mmInStream = socket.getInputStream();
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            while (true) {

                int len = 0;
                try {
                    len = checkHead(mmInStream);
                    if (len != -1) {
                        byte[] buf_data = new byte[len + 2];
                        System.arraycopy(bffer, 0, buf_data, 0, 2);
                        resetMemey(bffer);
                        fill(mmInStream, buf_data, 2, len);
                        Message msg = new Message();
                        msg.obj = onHandler(buf_data);
                        msg.what = 1;
                        LinkDetectedHandler.sendMessage(msg);
                    }
                }catch (IOException e) {
                    try {
                        mmInStream.close();
                    } catch (IOException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                    break;
                }
//                try {
//                    // Read from the InputStream
//                    if( (bytes = mmInStream.read(buffer)) > 0 )
//                    {
//                        byte[] buf_data = new byte[bytes];
//                        for(int i=0; i<bytes; i++)
//                        {
//                            buf_data[i] = buffer[i];
//                        }
//                        String s = new String(buf_data);
//                        Message msg = new Message();
//                        msg.obj = s;
//                        msg.what = 1;
//                        LinkDetectedHandler.sendMessage(msg);
//                    }
//                } catch (IOException e) {
//                    try {
//                        mmInStream.close();
//                    } catch (IOException e1) {
//                        // TODO Auto-generated catch block
//                        e1.printStackTrace();
//                    }
//                    break;
//                }
            }
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (BluetoothMsg.serviceOrCilent == BluetoothMsg.ServerOrCilent.CILENT)
        {
            shutdownClient();
        }
        else if (BluetoothMsg.serviceOrCilent == BluetoothMsg.ServerOrCilent.SERVICE)
        {
            shutdownServer();
        }
        BluetoothMsg.isOpen = false;
        BluetoothMsg.serviceOrCilent = BluetoothMsg.ServerOrCilent.NONE;
    }

    private byte[] setByte(){
        byte[] notBuff;
        notBuff = new byte[2];
        notBuff[0] = (byte) 1;
        notBuff[1] = (byte) 0;
        return notBuff;
    }

    private String onHandler(byte[] bytes) {
        String msg = "";
        switch (bytes[0]) {
            case 0:
                msg = "设备成功收到命令";
                break;
            case 1:
                if (bytes[1] == 6) {
                    switch (bytes[2]) {
                        case 1:
                            String temp1 = getIntByByte(bytes[3]) + "."
                                    + String.format("%03d", getIntByByte(bytes[4]));
                            String temp2 = getIntByByte(bytes[5]) + "."
                                    + String.format("%03d", getIntByByte(bytes[6]));
                            msg = temp1 + "," + temp2;
                            break;
                        case 2:
                            msg = "温湿度传感器信号超时";
                            break;
                        default:
                            msg = " 温湿度传感器,未知错误";
                            break;
                    }
                }
                break;
            default:
                msg = "无";
                break;
        }
        return msg;
    }
    private int getIntByByte(byte b) {
        int result = b;
        if (b < 0)
            result = (b + 256);
        return result;
    }

}
