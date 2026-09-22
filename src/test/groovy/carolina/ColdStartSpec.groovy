package carolina

import com.zaxxer.hikari.HikariConfig
import spock.lang.Specification

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class ColdStartSpec extends Specification {

    def setup() {
        CatalogService.resetCounts()
        CatalogService.resetRegistration()
        CatalogService.queryFn = null
        CatalogService.connectFn = null
    }

    def cleanup() {
        CatalogService.resetRegistration()
    }

    void 'startup registration returns while the CMS socket stalls and still POSTs'() {
        given:
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName('127.0.0.1'))
        AtomicReference<String> request = new AtomicReference<>()
        CountDownLatch accepted = new CountDownLatch(1)
        Thread holder = new Thread({
            Socket socket = server.accept()
            try {
                request.set(readRequest(socket))
                accepted.countDown()
                sleepUntilInterrupted()
            } finally {
                socket.close()
            }
        }, 'stall-cms')
        holder.daemon = true
        holder.start()
        String url = "http://127.0.0.1:${server.localPort}"

        when:
        long started = System.nanoTime()
        CatalogService.registerWithElixir(url, 'dev')
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L
        boolean posted = accepted.await(3, java.util.concurrent.TimeUnit.SECONDS)

        then:
        elapsedMs < 1500L
        posted
        request.get().contains('POST /internal/api-endpoints/register')
        request.get().contains('Authorization: Bearer dev')
        request.get().contains('Groovy')
        request.get().contains('Grails')
        request.get().contains('base_url')
        CatalogService.sqlCount == 0
        CatalogService.connectCount == 0
        file('grails-app/init/carolina/BootStrap.groovy').text.contains('CatalogService.registerWithElixir()')

        cleanup:
        holder.interrupt()
        server.close()
    }

    void 'blank registration settings do not open a connection'() {
        given:
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName('127.0.0.1'))
        server.soTimeout = 200

        when:
        CatalogService.registerWithElixir("http://127.0.0.1:${server.localPort}", '')
        CatalogService.registerWithElixir('', 'dev')
        CatalogService.registerWithElixir(null, null)
        Socket accepted = null
        try {
            accepted = server.accept()
        } catch (SocketTimeoutException ignored) {
            accepted = null
        }

        then:
        accepted == null
        CatalogService.sqlCount == 0
        CatalogService.connectCount == 0

        cleanup:
        accepted?.close()
        server?.close()
    }

    void 'catalog pool stays under its max and does not connect just to exist'() {
        when:
        HikariConfig cfg = CatalogService.buildPoolConfig()

        then:
        cfg.maximumPoolSize == CatalogService.POOL_MAX
        cfg.minimumIdle == CatalogService.POOL_MIN_IDLE
        cfg.maximumPoolSize >= 1
        cfg.maximumPoolSize <= 4
        cfg.minimumIdle < cfg.maximumPoolSize
        cfg.initializationFailTimeout < 0
        file('grails-app/services/carolina/CatalogService.groovy').text.contains('new HikariDataSource(buildPoolConfig())')
        !file('grails-app/init/carolina/BootStrap.groovy').text.contains('ensurePool')
        !file('grails-app/init/carolina/BootStrap.groovy').text.contains('HikariDataSource')
    }

    void 'fly suspends within 2gb and the JVM ram cap fits that memory'() {
        given:
        String fly = file('fly.toml').text
        String docker = file('Dockerfile').text
        def memory = (fly =~ /(?m)^\s*memory\s*=\s*"(\d+)(mb|gb)"/)
        assert memory.find()
        int memoryMb = memory.group(2) == 'gb' ? (memory.group(1) as int) * 1024 : memory.group(1) as int
        def grace = (fly =~ /grace_period\s*=\s*"(\d+)s"/)
        assert grace.find()
        def heap = (docker =~ /-Xmx(\d+)m/)
        assert heap.find()
        def metaspace = (docker =~ /MaxMetaspaceSize=(\d+)m/)
        assert metaspace.find()
        def codeCache = (docker =~ /ReservedCodeCacheSize=(\d+)m/)
        assert codeCache.find()
        int heapMb = heap.group(1) as int
        int reservedMb = (metaspace.group(1) as int) + (codeCache.group(1) as int)

        expect:
        fly.contains('auto_stop_machines = "suspend"')
        !fly.contains('auto_stop_machines = "stop"')
        !fly.toLowerCase().contains('swap')
        fly.contains('path = "/health"')
        fly.contains('min_machines_running = 0')
        memoryMb <= 2048
        (grace.group(1) as int) >= 40
        heapMb + reservedMb < memoryMb
        !docker.contains('MaxRAM=')
        docker.contains('-Dgrails.env=prod')
        docker.contains('openjdk:27')
    }

    private String readRequest(Socket socket) {
        socket.soTimeout = 2000
        InputStream input = socket.inputStream
        ByteArrayOutputStream buf = new ByteArrayOutputStream()
        byte[] chunk = new byte[4096]
        int contentLength = -1
        try {
            while (true) {
                int n = input.read(chunk)
                if (n < 0) {
                    break
                }
                buf.write(chunk, 0, n)
                String text = buf.toString('ISO-8859-1')
                int headerEnd = text.indexOf('\r\n\r\n')
                if (headerEnd >= 0 && contentLength < 0) {
                    def matcher = (text =~ /(?i)Content-Length:\s*(\d+)/)
                    contentLength = matcher.find() ? (matcher.group(1) as int) : 0
                }
                if (headerEnd >= 0 && contentLength >= 0 && buf.size() >= headerEnd + 4 + contentLength) {
                    break
                }
            }
        } catch (SocketTimeoutException ignored) {
            // Return whatever the client managed to send.
        }
        buf.toString('UTF-8')
    }

    private void sleepUntilInterrupted() {
        try {
            Thread.sleep(20000)
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt()
        }
    }

    private File file(String path) {
        new File(System.getProperty('user.dir'), path)
    }
}
