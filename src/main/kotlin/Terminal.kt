import java.io.File
import java.net.SocketTimeoutException
import kotlin.system.exitProcess

class Terminal(
    inetAddress: String = "",
    domain: String = "",
    port: Int? = null
) {
    private val wc = WebClient(inetAddress, domain, port)
    private val webClient = wc.getApi()
    private var username = ""
    private var password = ""
    private var token = ""
    private var path = ""
    private val host = wc.host

    companion object {
        const val MSG_KICKED = "Ваш сеанс был завершён сервером или другим пользователем."
        const val MSG_UNKNOWN_CODE = "ERR. Сервер прислал сообщение с неизвестным кодом."
    }

    init {
        auth()
    }

    private fun auth() {
        while (username == "" && password == "") {
            var un: String
            var pass: String
            while (true) {
                println("Введите username:")
                un = readLine() ?: exitProcess(0)

                if (un.isEmpty() || un.split(" ").size > 1)
                    println("Некорректный username")
                else
                    break
            }
            while (true) {
                println("Введите пароль:")
                pass = readLine() ?: exitProcess(0)

                if (pass.isEmpty())
                    println("Некорректный пароль")
                else
                    break
            }

            println("Подождите, подключаемся к серверу.")

            val call = webClient.auth(un, pass)
            try {
                val response = call.execute()

                when (response.code()) {
                    Code.OK.int -> {
                        val userInfo = response.body()
                        username = un
                        password = pass
                        token = "Bearer ${userInfo!!.token}"
                        path = userInfo.currentDir
                        println("\nАвторизация прошла успешно.\nДобрый день, $username!")
                        break
                    }
                    Code.PERMISSION_DENIED.int -> println("Пользователь уже используется.")
                    Code.NEED_AUTHORIZATION.int -> println("Введён неверный логин или пароль. Повторите попытку.")
                    else -> println("$MSG_UNKNOWN_CODE. Code = ${response.code()}")
                }
            } catch (e: Exception) {
                exceptHandle(e)
            }
        }

        readConsole()
    }

    private fun readConsole() {
        println("Используйте help, чтобы узнать список доступных команд.\n")
        while (true) {
            print("$username@$host:$path$ ")
            val command = readLine()

            if (command == null || command.startsWith("logout"))
                logout()

            if (command!!.isEmpty()) {
                println("Введена некорректная команда")
                continue
            }

            val comArgs = command.split(" ")

            when (comArgs[0]) {
                "help" ->
                    println(
                        "Поддерживаются следующие команды:\n" +
                                "\tls – выдача содержимого каталога;\n" +
                                "\tcd dir - смена текущего каталога на dir;\n" +
                                "\twho - выдача списка зарегистрированных пользователей с указанием их текущего каталога;\n" +
                                "\tkill username - завершение сеанса пользователя username (привилегированная команда);\n" +
                                "\tlogout - выход из системы."
                    )

                "cd" ->
                    if ((comArgs.size > 2 && command.matches(Regex("cd \"[^/\\:*?\"<>|]*\""))) ||
                        (comArgs.size == 2 && comArgs[1].isNotEmpty() && (!comArgs[1].contains(Regex("[\\/:*?\"<>|]"))) ||
                                comArgs[1].contains("../"))
                    )
                        cd(command.removePrefix("cd ").trim())
                    else
                        println("Директория указана неверно.")

                "kill" ->
                    if (comArgs.size > 2)
                        println("Имя пользователя не должно содержать пробел.")
                    else
                        kill(comArgs[1])

                "ls" -> ls()

                "who" -> who()

                else -> println("Введена некорректная команда. Используйте help, чтобы узнать список доступных команд.")
            }
        }
    }

    private fun ls() {
        val call = webClient.ls(token)

        try {
            val response = call.execute()

            when (response.code()) {
                Code.OK.int -> {
                    val filesList = response.body()

                    if (filesList == null)
                        println("ERR: Сервер не прислал список файлов")
                    else {
                        val str = StringBuffer("")
                        val sortFiles = sortFiles(filesList)

                        for (i in sortFiles.indices) {
                            str.append(sortFiles[i])
                            if (i != sortFiles.indices.last)
                                str.append("\n")
                        }
                        println(str)
                    }
                }
                Code.WAS_KICKED.int -> exit(MSG_KICKED)
                Code.NEED_AUTHORIZATION.int -> {
                    val res = reAuth()
                    if (res)    // Повтор команды, только если смогли вернуться в директорию
                        ls()
                }
                else -> println("$MSG_UNKNOWN_CODE. Code = ${response.code()}")
            }
        } catch (e: Exception) {
            exceptHandle(e)
        }
    }

    // true - успешно
    private fun cd(dirName: String, fullPath: Boolean = false): Boolean {
        val dirPath = if (fullPath)
            dirName
        else
            if (dirName.contains("../")) {
                val dirs = dirName.split("/")
                val currPath = path.split(File.separator).toMutableList()
                var countBack = 0
                for (el in dirs) {
                    if (el == "..")
                        countBack++
                }
                currPath.dropLast(countBack).joinToString(separator = File.separator)
            } else
                path + File.separator + dirName

        val call = webClient.cd(dirPath, token)

        try {
            val response = call.execute()
            when (response.code()) {
                Code.OK.int -> {
                    val currentDir = response.body()
                    path = currentDir!!.path
                    return true
                }
                Code.BAD_REQUEST.int -> println("Указанная директория не существует.")
                Code.WAS_KICKED.int -> exit(MSG_KICKED)
                Code.NEED_AUTHORIZATION.int -> {
                    reAuth()
                    return cd(dirPath, fullPath)
                }
                else -> println("$MSG_UNKNOWN_CODE. Code = ${response.code()}")
            }
            return false
        } catch (e: Exception) {
            exceptHandle(e)
        }
        return false
    }

    private fun who() {
        val call = webClient.who(token)

        try {
            val response = call.execute()
            when (response.code()) {
                Code.OK.int -> {
                    val userList = response.body()
                    if (userList == null)
                        println("ERR. Сервер прислал некорректный ответ.")
                    else {
                        val str = StringBuffer("")
                        val last = userList.keys.last()
                        val maxLen = userList.keys.maxOf { it.length }

                        for (userPath in userList) {
                            str.append("${userPath.key.padEnd(maxLen, ' ')}\t\t\t${userPath.value}")
                            if (userPath.key != last)
                                str.append("\n")
                        }
                        println(str)
                    }
                }
                Code.WAS_KICKED.int -> exit(MSG_KICKED)
                Code.NEED_AUTHORIZATION.int -> {
                    reAuth()
                    who()
                }
                else -> println("$MSG_UNKNOWN_CODE. Code = ${response.code()}")
            }
        } catch (e: Exception) {
            exceptHandle(e)
        }
    }

    private fun kill(username: String) {
        val call = webClient.kill(username, token)

        try {
            val response = call.execute()

            when (response.code()) {
                Code.OK.int -> println("Сеанс пользователя $username успешно завершён.")
                Code.BAD_REQUEST.int -> println("Пользователь с указанным username не найден.")
                Code.PERMISSION_DENIED.int -> println("Permission denied: У вас нет разрешения на использование данной команды.")
                Code.WAS_KICKED.int -> exit(MSG_KICKED)
                Code.NEED_AUTHORIZATION.int -> {
                    reAuth()
                    kill(username)
                }
                else -> println("$MSG_UNKNOWN_CODE. Code = ${response.code()}")
            }
        } catch (e: Exception) {
            exceptHandle(e)
        }
    }

    private fun logout() {
        println("Получена команда завершения работы.")
        println("Отключаемся от сервера.")
        val call = webClient.logout(token)
        try {
            call.execute()
        } catch (_: Exception) {
        } finally {
            println("Отключение выполнено. Завершение работы.")
            exitProcess(0)
        }
    }

    // true - директория восстановлена
    // false - директория не восстановлена
    private fun reAuth(): Boolean {
        println("Время вашего сеанса истекло. Пробуем переподключиться к серверу.")
        val callAuth = webClient.auth(username, password)
        try {
            val respAuth = callAuth.execute()
            val userInfo = respAuth.body()

            if (respAuth.code() != Code.OK.int)
                exit("Произошла ошибка при попытке переподключения к серверу.")
            else {
                token = "Bearer ${userInfo!!.token}"
                println("Подключение произошло успешно. Восстанавливаем рабочую директорию.")

                return if (cd(path, true)) {
                    println("Рабочая директория успешно восстановлена. Вы можете продолжить работу.")
                    true
                } else {
                    println("Произошла ошибка при восстановлении рабочей директории. Вы продолжите работу с начальной директории.")
                    false
                }
            }
        } catch (e: Exception) {
            exceptHandle(e)
            return false
        }
        return true
    }

    private fun sortFiles(files: List<String>): List<String> {
        val dirList = mutableListOf<String>()
        val filesList = mutableListOf<String>()

        for (file in files) {
            if (file.startsWith("/"))
                dirList.add(file)
            else
                filesList.add(file)
        }
        dirList.sort()
        filesList.sort()
        dirList.addAll(filesList)
        return dirList
    }

    private fun exit(msg: String?) {
        if (msg != null)
            println(msg)
        println("Завершение работы.")
        exitProcess(0)
    }

    private fun exceptHandle(e: Exception) {
        if (e::class.java == SocketTimeoutException::class.java)
            println("Превышено время ожидания ответа от сервера.")
        else {
            println("Возникла проблема при обращении к серверу.")
            println("${e::class.java}: ${e.message}")
        }
        exit(null)
    }

    enum class Code(val int: Int) {
        OK(200),
        BAD_REQUEST(400),
        NEED_AUTHORIZATION(401),
        PERMISSION_DENIED(403),
        WAS_KICKED(418)
    }
}