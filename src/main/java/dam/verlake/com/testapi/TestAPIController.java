package dam.verlake.com.testapi;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
public class TestAPIController {

    @GetMapping("/api/test")
    public String getTest() {
        return "GET request received! This is the response from the Test API.";
    }

    @PostMapping("/api/test")
    public String postTest() {
        return "POST request received! This is the response from the Test API.";
    }

    // API to handle POST form data
    @PostMapping("/api/test/form")
    public Map<String, String> postForm(@RequestParam String name, @RequestParam String email) {
        Map<String, String> response = new HashMap<>();
        response.put("name", name);
        response.put("email", email);
        return response;
    }

    // API to handle POST JSON data
    @PostMapping("/api/test/json")
    public Map<String, Object> postJson(@RequestBody Map<String, Object> json) {
        Map<String, Object> response = new HashMap<>();
        response.put("received", json);
        return response;
    }

    // API to handle file upload and parameters
    @PostMapping("/api/test/file")
    public Map<String, Object> uploadFile(@RequestParam("file") MultipartFile file,
                                          @RequestParam("description") String description) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("fileName", file.getOriginalFilename());
        response.put("fileSize", file.getSize());
        response.put("description", description);
        return response;
    }

    // API to handle GET with query parameters
    @GetMapping("/api/test/params")
    public Map<String, String> getWithParams(@RequestParam String key, @RequestParam String value) {
        Map<String, String> response = new HashMap<>();
        response.put("key", key);
        response.put("value", value);
        return response;
    }
}