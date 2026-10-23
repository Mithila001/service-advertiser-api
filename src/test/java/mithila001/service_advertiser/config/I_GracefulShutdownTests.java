package mithila001.service_advertiser.config;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import standalone.shutdownfixture.ShutdownTestApplication;
class I_GracefulShutdownTests {
    private static URI uri(ServletWebServerApplicationContext c,String path) { return URI.create("http://localhost:"+c.getWebServer().getPort()+path); }
    @Test void realChunkedRequestsRespectTheByteBoundary() throws Exception {
        var context=ShutdownTestApplication.start();try(var client=HttpClient.newHttpClient()) {
            for(int size:new int[]{65535,65536,65537}) {
                byte[] body=new byte[size];var request=HttpRequest.newBuilder(uri(context,"/api/v1/probe")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofInputStream(()->new java.io.ByteArrayInputStream(body))).build();
                var response=client.send(request,HttpResponse.BodyHandlers.ofString());assertEquals(size>65536?413:200,response.statusCode());
                if(size<=65536)assertEquals(Integer.toString(size),response.body());assertTrue(response.headers().firstValue("X-Request-ID").isPresent());
            }
        }finally{context.close();}
    }
    @Test void activeRequestFinishesDuringGracefulContextShutdown() throws Exception {
        var context=ShutdownTestApplication.start();var probe=context.getBean(ShutdownTestApplication.Probe.class);int port=context.getWebServer().getPort();
        try(var client=HttpClient.newHttpClient();var executor=Executors.newSingleThreadExecutor()) {
            var response=client.sendAsync(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/categories/shutdown-probe")).GET().build(),HttpResponse.BodyHandlers.ofString());assertTrue(probe.entered.await(5,TimeUnit.SECONDS));
            var closing=executor.submit(context::close);try{assertThrows(TimeoutException.class,()->closing.get(200,TimeUnit.MILLISECONDS));}finally{probe.release.countDown();}
            assertEquals(200,response.get(5,TimeUnit.SECONDS).statusCode());closing.get(5,TimeUnit.SECONDS);
        }finally{probe.release.countDown();if(context.isActive())context.close();}
    }
    @Test void shutdownGracePeriodBoundsAStalledRequest() throws Exception {
        var context=ShutdownTestApplication.start();var probe=context.getBean(ShutdownTestApplication.Probe.class);int port=context.getWebServer().getPort();
        try(var client=HttpClient.newHttpClient();var executor=Executors.newSingleThreadExecutor()) {
            client.sendAsync(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/categories/shutdown-probe")).GET().build(),HttpResponse.BodyHandlers.discarding());assertTrue(probe.entered.await(5,TimeUnit.SECONDS));
            long started=System.nanoTime();executor.submit(context::close).get(5,TimeUnit.SECONDS);assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-started)<5);
        }finally{probe.release.countDown();if(context.isActive())context.close();}
    }
}
