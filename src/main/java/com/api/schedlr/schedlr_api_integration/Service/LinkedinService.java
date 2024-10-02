package com.api.schedlr.schedlr_api_integration.Service;
import com.api.schedlr.schedlr_api_integration.Constants.APIConstants;
import com.api.schedlr.schedlr_api_integration.DTOs.LinkedInData;
import com.api.schedlr.schedlr_api_integration.repo.ProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LinkedinService {

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ProfileRepository profileRepository;

    public String uploadPostLinkedIn(int userId, MultipartFile image, String postDescription){
        System.out.println("Called uploadPostLinkedIn");
        Optional<LinkedInData> data = getLinkedInPersonId(userId);
        if(data==null){
           return "Failed to retreive from DB";
        }
        String accessToken = data.get().getAccessToken();
        String personId = data.get().getPersonId();

        Map<String, String> uploadImageJson = registerUpload(accessToken, personId);
        System.out.println("JSON Response: "+uploadImageJson);
        if(uploadImageJson==null){
            return "Failed to upload Image in the url";
        }
        String asset=uploadImageJson.get("asset");
        String uploadUrl=uploadImageJson.get("uploadUrl");
//        String asset = (String) uploadImageJson.get("asset");
//        // Extract the uploadMechanism map first
//        Map<String, Object> uploadMechanism = (Map<String, Object>) valueMap.get("uploadMechanism");
//
//// Get the inner map for MediaUploadHttpRequest
//        Map<String, Object> mediaUploadHttpRequest = (Map<String, Object>) uploadMechanism.get("com.linkedin.digitalmedia.uploading.MediaUploadHttpRequest");
//
//// Now extract the uploadUrl
//        String uploadUrl = (String) mediaUploadHttpRequest.get("uploadUrl");
        System.out.println("Image : "+ image);
        uploadImage(uploadUrl, image, accessToken);

        createShare(accessToken, asset, String.valueOf(userId), postDescription);


        return "Ok";
    }

    public Optional<LinkedInData> getLinkedInPersonId(int userId) {
        List<Object[]> listOfRows= profileRepository.findLinkedInTokenAndPersonIdByUserId(userId);
        if (listOfRows.size()!=0) {
            Object[] data = listOfRows.get(0);
            String accessToken = (String) data[0];
            String personId = (String) data[1];
            return Optional.of(new LinkedInData(accessToken, personId));
        }
        return Optional.empty();
    }

    public Map<String, String> registerUpload(String accessToken, String personId) {
        String url = "https://api.linkedin.com/v2/assets?action=registerUpload";

        try {
            // Create the dynamic JSON request body
            String requestBody = createRegisterUploadRequestJson(personId);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Content-Type", "application/json");

            // Create an HttpEntity with the headers and body
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            // Send the POST request
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            // Parse the response body
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode responseBody = objectMapper.readTree(response.getBody());

            // Extract asset and uploadUrl values
            String asset = responseBody.path("value").path("asset").asText();
            String uploadUrl = responseBody.path("value")
                    .path("uploadMechanism")
                    .path("com.linkedin.digitalmedia.uploading.MediaUploadHttpRequest")
                    .path("uploadUrl").asText();

            // Return asset and uploadUrl in a Map
            Map<String, String> result = new HashMap<>();
            result.put("asset", asset);
            result.put("uploadUrl", uploadUrl);

            System.out.println("uploadUrl : "+ uploadUrl);
            System.out.println("asset : "+ asset);

            return result;

        } catch (HttpClientErrorException e) {
            // Handle error responses (e.g., 401 Unauthorized, 403 Forbidden, etc.)
            throw new RuntimeException("Error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }

    private String createRegisterUploadRequestJson(String personId) throws Exception {
        // Create the JSON structure using a Map
        Map<String, Object> requestBody = new HashMap<>();

        Map<String, Object> registerUploadRequest = new HashMap<>();
        registerUploadRequest.put("recipes", new String[]{"urn:li:digitalmediaRecipe:feedshare-image"});
        registerUploadRequest.put("owner", "urn:li:person:" + personId);

        Map<String, Object> serviceRelationship = new HashMap<>();
        serviceRelationship.put("relationshipType", "OWNER");
        serviceRelationship.put("identifier", "urn:li:userGeneratedContent");

        registerUploadRequest.put("serviceRelationships", new Object[]{serviceRelationship});

        requestBody.put("registerUploadRequest", registerUploadRequest);

        // Convert the Map to JSON using ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(requestBody);
    }

    public ResponseEntity<String> uploadImage(String uploadUrl, MultipartFile imageFile, String accessToken) {
        try {
            System.out.println("Called uploadImage" + uploadUrl);
            // Validate the URL
            URI uri = UriComponentsBuilder.fromUriString(uploadUrl).build().toUri();

            // Convert MultipartFile to a temporary File (if necessary)
            File tempFile = File.createTempFile("upload", imageFile.getOriginalFilename());
            imageFile.transferTo(tempFile);

            // Create headers
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);  // Set the Bearer token for LinkedIn API
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);  // Set content type as binary stream

            // Create HttpEntity with file data
            HttpEntity<FileSystemResource> requestEntity = new HttpEntity<>(new FileSystemResource(tempFile), headers);
            System.out.println("Called uploadImage 2 : "+ uri);
            // Execute the request using the valid URI
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST, requestEntity, String.class);

            System.out.println("Upload Response: " + response);

            // Clean up the temporary file
            boolean isDeleted = tempFile.delete();
            if (!isDeleted) {
                System.out.println("Temporary file deletion failed: " + tempFile.getAbsolutePath());
            }

            return response;
        } catch (IOException e) {
            System.err.println("File processing failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File processing failed");
        } catch (Exception e) {
            System.err.println("File upload failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File upload failed");
        }
    }


    // Step 2: Create the share with the uploaded media
    public ResponseEntity<String> createShare(String accessToken, String asset, String userId, String commentary) {
        String url = "https://api.linkedin.com/v2/ugcPosts";

        try {
            // Create the request body
            Map<String, Object> shareBody = new HashMap<>();
            shareBody.put("author", "urn:li:person:" + userId);
            shareBody.put("lifecycleState", "PUBLISHED");

            Map<String, Object> specificContent = new HashMap<>();
            Map<String, Object> shareContent = new HashMap<>();
            Map<String, Object> shareCommentary = new HashMap<>();
            shareCommentary.put("text", commentary);
            shareContent.put("shareCommentary", shareCommentary);
            shareContent.put("shareMediaCategory", "IMAGE");

            Map<String, Object> media = new HashMap<>();
            media.put("status", "READY");
            media.put("media", asset);
            specificContent.put("com.linkedin.ugc.ShareContent", shareContent);
            specificContent.put("media", new Map[]{media});

            shareBody.put("specificContent", specificContent);

            Map<String, Object> visibility = new HashMap<>();
            visibility.put("com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC");
            shareBody.put("visibility", visibility);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Create HttpEntity with the headers and body
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(shareBody, headers);

            // Send the POST request
            return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Share creation failed");
        }
    }






}
