package backend

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import at.favre.lib.crypto.bcrypt.BCrypt

@Serializable
data class RegisterRequest(val username: String, val password: String)

@Serializable
data class User(val username: String, val passwordHash: String, val friends: List<String> = emptyList(), val incoming: List<String> = emptyList(), val outgoing: List<String> = emptyList())

object Storage {
    private val file = File("backend_data.json")
    private val json = Json { prettyPrint = true }
    private var users: MutableMap<String, User> = mutableMapOf()

    init {
        if (file.exists()) {
            val text = file.readText()
            try {
                val map = json.decodeFromString(MapSerializer(String.serializer(), User.serializer()), text)
                users = map.toMutableMap()
            } catch (e: Exception) {
                users = mutableMapOf()
            }
        }
    }

    fun save() {
        val text = json.encodeToString(MapSerializer(String.serializer(), User.serializer()), users)
        file.writeText(text)
    }

    fun addUser(user: User): Boolean {
        if (users.containsKey(user.username)) return false
        users[user.username] = user
        save()
        return true
    }

    fun getUser(username: String): User? = users[username]
}

fun main() {
    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) {
            json()
        }

        routing {
            post("/register") {
                val req = call.receive<RegisterRequest>()
                if (req.username.length < 3) {
                    call.respond(mapOf("error" to "username too short"))
                    return@post
                }
                if (Storage.getUser(req.username) != null) {
                    call.respond(mapOf("error" to "username exists"))
                    return@post
                }
                val hash = BCrypt.withDefaults().hashToString(12, req.password.toCharArray())
                val user = User(req.username, hash)
                Storage.addUser(user)
                call.respond(mapOf("ok" to true))
            }

            get("/user/{username}") {
                val username = call.parameters["username"] ?: ""
                val user = Storage.getUser(username)
                if (user == null) {
                    call.respond(mapOf("error" to "not_found"))
                } else {
                    call.respond(user)
                }
            }

            // Note: This backend is a prototype. Authentication and security are minimal.
        }
    }.start(wait = true)
}
