import org.kohsuke.args4j.CmdLineException
import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option

class Launcher {
    @Option(name = "-a", usage = "inetAddress")
    private var inetAddress = ""

    @Option(name = "-d", usage = "domain")
    private var domain = ""

    @Option(name = "-p", usage = "port")
    private var port: Int? = null

    fun launch(args: Array<String>) {
        val parser = CmdLineParser(this)
        try {
            parser.parseArgument(*args)
        } catch (e: CmdLineException) {
            System.err.println(e.message)
            System.err.println(
                "Usage: java -jar remote_terminal_client.jar [-a inetAddress] [-p port]\n" +
                        "   or: java -jar remote_terminal_client.jar [-d domain] [-p port]"
            )
            parser.printUsage(System.err)
        }
        try {
            when {
                inetAddress != "" && domain != "" ->
                    println("Необходимо указать что-то одно: IP-адрес или доменное имя.")
                (inetAddress != "" || domain != "") && port == null ->
                    println("При указании IP-адреса или доменного имени необходимо указать порт.")
                port != null && inetAddress == "" && domain == "" ->
                    println("При указании порта необходимо указать IP-адрес или доменное имя.")
                else -> Terminal(inetAddress, domain, port)
            }
        } catch (e: Exception) {
            System.err.println(e.message)
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Launcher().launch(args)
        }
    }
}