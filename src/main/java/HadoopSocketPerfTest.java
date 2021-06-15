import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.net.StandardSocketFactory;
import javax.net.SocketFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

public class HadoopSocketPerfTest {
  private static String host;
  private static int port;
  private static final int timeout = 60000;

  @ChannelHandler.Sharable
  private static class EchoHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
      ctx.writeAndFlush(msg);
    }
  }

  private static void startServer() throws Exception {
    ChannelHandler serverHandler = new EchoHandler();
    for (int i = 0; i < 100; ++i) {
      ServerBootstrap b = new ServerBootstrap();
      b.group(new NioEventLoopGroup(1), new NioEventLoopGroup(2))
          .channel(NioServerSocketChannel.class)
          .option(ChannelOption.SO_BACKLOG, 512)
          .childOption(ChannelOption.SO_TIMEOUT, timeout)
          .childOption(ChannelOption.TCP_NODELAY, true)
          .childHandler(serverHandler);
      ChannelFuture f = b.bind(host, port + i).sync();
    }
    Thread.sleep(Integer.MAX_VALUE);
  }

  private static void startClient(int threadCnt) throws Exception {
    SocketFactory factory = new StandardSocketFactory();
    Thread[] tArray = new Thread[threadCnt];
    CountDownLatch latch = new CountDownLatch(threadCnt);
    for (int i = 0; i < threadCnt; ++i) {
      final int curPort = port + (i % 100);
      Thread t = new Thread(() -> {
        try {
          Socket socket = factory.createSocket();
          socket.setTcpNoDelay(true);
          socket.setKeepAlive(false);
          NetUtils.connect(socket, new java.net.InetSocketAddress(host, curPort), timeout);
          socket.setSoTimeout(timeout);

          InputStream inStream = NetUtils.getInputStream(socket);
          OutputStream outStream = NetUtils.getOutputStream(socket, timeout);
          DataInputStream in = new DataInputStream(new BufferedInputStream(inStream));
          DataOutputStream out = new DataOutputStream(new BufferedOutputStream(outStream));

          byte[] buf = new byte[256];
          for (int j = 0; j < 1024; ++j) {
            out.write(buf);
            out.flush();
            in.readFully(buf);
          }

          out.close();
          in.close();
          socket.close();
        } catch (Exception e) {
          throw new RuntimeException(e);
        } finally {
          latch.countDown();
        }
      });
      tArray[i] = t;
    }

    for (int i = 0; i < threadCnt; ++i) {
      tArray[i].start();
    }
    latch.await();
  }

  public static void main(String[] args) throws Exception {
    String type = args[0];
    host = args[1];
    port = Integer.parseInt(args[2]);
    if ("server".equalsIgnoreCase(type)) {
      startServer();
    } else {
      int threadCnt = Integer.parseInt(args[3]);
      startClient(1);
      Thread.sleep(1000);

      long start = System.currentTimeMillis();
      startClient(threadCnt);
      long end = System.currentTimeMillis();
      System.out.println("cost: " + (end - start) + "(ms)");
    }
  }
}
