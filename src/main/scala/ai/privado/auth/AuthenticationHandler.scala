package ai.privado.auth
import org.slf4j.LoggerFactory

import java.math.BigInteger
import java.io.File
import java.security.MessageDigest

object AuthenticationHandler {
  /*
   * To handle the cloud flow for scanned repositories. Assumes the flag for auth is enabled.
   * Asks for consent from the user and then decides the flow for Privado Cloud APIs.
   */
  private val logger          = LoggerFactory.getLogger(this.getClass)
  val userHash: String        = sys.env.getOrElse("PRIVADO_USER_HASH", null)
  val dockerAccessKey: String = sys.env.getOrElse("PRIVADO_DOCKER_ACCESS_KEY", null)
  def syncToCloud: Boolean = {
    try {
      sys.env.getOrElse("PRIVADO_SYNC_TO_CLOUD", "False").toBoolean
    } catch {
      case _: Exception => false
    }
  }

  def authenticate(repoPath: String): Unit = {
    dockerAccessKey match {
      case null => () // No auth flow happens if docker access key is not present
      case _ =>
        var syncPermission: Boolean = true
        if (!syncToCloud) {
          syncPermission = askForPermission() // Ask user for request permissions
        }
        if (syncPermission) {
          println(pushDataToCloud(repoPath))
        } else {
          ()
        }
    }
  }

  def askForPermission(): Boolean = {
    println("Do you want to visualize these results on our Privacy View Cloud Dashboard? (Y/n)")
    val userPermissionInput = scala.io.StdIn.readLine().toLowerCase
    userPermissionInput match {
      case "n" | "no" | "0" => false
      case _ =>
        updateConfigFile("syncToPrivadoCloud", "true")
        true
    }
  }

  def updateConfigFile(property: String, value: String): Boolean = {
    try {
      val jsonString = os.read(os.Path("/app/config/config.json"))
      val data       = ujson.read(jsonString)
      data(property) = value
      os.write.over(os.Path("/app/config/config.json"), data)
      true
    } catch {
      case e: Exception =>
        logger.debug(s"Error while updating the config file")
        logger.error(s"${e.toString}")
        false
    }
  }

  def pushDataToCloud(repoPath: String): String = {
    // TODO change BASE_URL and upload url for prod
    val BASE_URL          = "https://t.api.code.privado.ai/test"
    val file              = new File(s"$repoPath/.privado/privado.json")
    val uploadURL: String = s"$BASE_URL/cli/api/file/$userHash"

    val accessKey: String = {
      String.format(
        "%032x",
        new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(dockerAccessKey.getBytes("UTF-8")))
      )
    }

    try {
      val response = requests.post(
        uploadURL,
        data = requests.MultiPart(requests.MultiItem("scanfile", file, file.getName)),
        headers = Map("access-key" -> s"$accessKey")
      )
      val json = ujson.read(response.text)
      response.statusCode match {
        case 200 => json("redirectUrl").toString()
        case _   => json("message").toString()
      }

    } catch {
      case e: Exception => s"Error Occurred. ${e.toString}"
    }
  }
}
