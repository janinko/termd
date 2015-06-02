package io.termd.core.http.undertow;

import io.termd.core.io.BinaryDecoder;
import io.termd.core.readline.KeyDecoder;
import io.termd.core.readline.Keymap;
import io.termd.core.readline.Readline;
import io.termd.core.tty.Signal;
import io.termd.core.tty.TtyConnection;
import io.termd.core.util.Handler;
import io.termd.core.util.Helper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class NativeProcessBootstrap implements Handler<TtyConnection> {


  @Override
  public void handle(final TtyConnection conn) {
    InputStream inputrc = KeyDecoder.class.getResourceAsStream("inputrc");
    Keymap keymap = new Keymap(inputrc);
    Readline readline = new Readline(keymap);
    for (io.termd.core.readline.Function function : Helper.loadServices(Thread.currentThread().getContextClassLoader(), io.termd.core.readline.Function.class)) {
      System.out.println("Server is adding function to readline:" + function); //TODO log trace
      readline.addFunction(function);
    }
    conn.setTermHandler(new Handler<String>() {
      @Override
      public void handle(String term) {
        // Not used yet but we should propagage this to the process builder
        System.out.println("CLIENT $TERM=" + term);
      }
    });
    conn.writeHandler().handle(Helper.toCodePoints("Welcome sir\r\n"));
    read(conn, readline);
  }

  public void read(final TtyConnection conn, final Readline readline) {
    Handler<String> requestHandler = new Handler<String>() {
      @Override
      public void handle(String line) {
        Task task = new Task(conn, readline, line);
        task.start();
      }
    };
    readline.readline(conn, "% ", requestHandler);
  }

  class Task extends Thread {

    final TtyConnection conn;
    final Readline readline;
    final String line;

    public Task(TtyConnection conn, Readline readline, String line) {
      this.conn = conn;
      this.readline = readline;
      this.line = line;
    }

    private class Pipe extends Thread {

      private final Charset charset = StandardCharsets.UTF_8; // We suppose the process out/err uses UTF-8
      private final InputStream in;
      private final BinaryDecoder decoder = new BinaryDecoder(charset, new Handler<int[]>() {
        @Override
        public void handle(final int[] codepoints) {
          conn.schedule(new Runnable() {
            @Override
            public void run() {

              // Replace any \n by \r\n (need to improve that somehow...)
              int len = codepoints.length;
              for (int i = 0;i < codepoints.length;i++) {
                if (codepoints[i] == '\n' && (i == 0 || codepoints[i -1] != '\r')) {
                  len++;
                }
              }
              int ptr = 0;
              int[] corrected = new int[len];
              for (int i = 0;i < codepoints.length;i++) {
                if (codepoints[i] == '\n' && (i == 0 || codepoints[i -1] != '\r')) {
                  corrected[ptr++] = '\r';
                  corrected[ptr++] = '\n';
                } else {
                  corrected[ptr++] = codepoints[i];
                }
              }

              conn.writeHandler().handle(corrected);
            }
          });
        }
      });

      public Pipe(InputStream in) {
        this.in = in;
      }

      @Override
      public void run() {
        byte[] buffer = new byte[512];
        while (true) {
          try {
            int l = in.read(buffer);
            if (l == -1) {
              break;
            }
            decoder.write(buffer, 0, l);
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }

    @Override
    public void run() {
      ProcessBuilder builder = new ProcessBuilder(line.split("\\s+"));
      try {
        final Process process = builder.start();
        conn.setSignalHandler(new Handler<Signal>() {
          boolean interrupted; // Signal state
          @Override
          public void handle(Signal signal) {
            if (signal == Signal.INT) {
              if (!interrupted) {
                interrupted = true;
                process.destroy();
              }
            }
          }
        });
        Pipe stdout = new Pipe(process.getInputStream());
        Pipe stderr = new Pipe(process.getErrorStream());
        stdout.start();
        stderr.start();
        try {
          process.waitFor();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        try {
          stdout.join();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        try {
          stderr.join();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      } catch (IOException e) {
        conn.writeHandler().handle(Helper.toCodePoints(e.getMessage() + "\r\n"));
      }

      // Read line again
      conn.setSignalHandler(null);
      conn.schedule(new Runnable() {
        @Override
        public void run() {
          read(conn, readline);
        }
      });
    }
  }

  public static void main(String[] args) throws Exception {
    start("localhost", 8080, null);
  }

  public static void start(String host, int port, final Runnable onStart) throws InterruptedException {
    WebSocketBootstrap bootstrap = new WebSocketBootstrap(
        "localhost",
        8080,
        new NativeProcessBootstrap());
//    final CountDownLatch latch = new CountDownLatch(1);
    bootstrap.bootstrap(new Handler<Boolean>() {
      @Override
      public void handle(Boolean event) {
        if (event) {
          System.out.println("Server started on " + 8080);
          if (onStart != null) onStart.run();
        } else {
          System.out.println("Could not start");
//          latch.countDown();
        }
      }
    });
//    latch.await();
  }
}
