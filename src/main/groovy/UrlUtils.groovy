import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader

class UrlUtils {

    private static Gson gson = new Gson()

    static JsonObject jsonFromUrl(String url) {
        URL urlObject = URI.create(url).toURL()
        URLConnection connection = urlObject.openConnection()
        InputStreamReader reader = new InputStreamReader(connection.getInputStream())

        JsonReader jsonReader = new JsonReader(reader)
        JsonObject jsonObject = gson.fromJson(jsonReader, JsonObject)

        jsonReader.close()
        reader.close()
        return jsonObject
    }

    static String downloadFromUrl(String url, File output) {
        File parentDirectory = output.parentFile
        if(!parentDirectory.exists()) {
            parentDirectory.mkdirs()
        }
        URL urlObject = URI.create(url).toURL()
        URLConnection connection = urlObject.openConnection()
        InputStream inStream = connection.getInputStream()
        OutputStream outStream = new FileOutputStream(output)
        inStream.transferTo(outStream)
        outStream.flush()
        outStream.close()
        inStream.close()
        return output // TODO 2026-04-27: Validate this
    }

}