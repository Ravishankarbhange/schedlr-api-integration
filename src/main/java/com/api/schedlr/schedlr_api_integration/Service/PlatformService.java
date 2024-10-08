package com.api.schedlr.schedlr_api_integration.Service;

import com.api.schedlr.schedlr_api_integration.Constants.APIConstants;
import com.api.schedlr.schedlr_api_integration.entity.Profile;
import com.api.schedlr.schedlr_api_integration.entity.User;
import com.api.schedlr.schedlr_api_integration.repo.ProfileRepository;
import com.api.schedlr.schedlr_api_integration.repo.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class PlatformService {

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private UserRepository userRepository;

    public Profile updateLinkedInAccessToken(int userId, String linkedInAccessToken, String linkedInPersonId) {
        // Fetch the User entity using userId
        Optional<User> userOptional = userRepository.findById(userId);
        // Check if the user exists
        if (!userOptional.isPresent()) {
            throw new IllegalArgumentException("User with ID " + userId + " not found.");
        }

        // Get the User object
        User user = userOptional.get();

        // Get current date and add 45 days
        LocalDateTime linkedInAccessTokenExpireDate = LocalDateTime.now().plusDays(45);

        Optional<Profile> existingProfile = profileRepository.findByUserId(userId);
        // If profile exists, update the LinkedIn-related fields if they are null
        if (existingProfile.isPresent()) {
            Profile profile = existingProfile.get();

            // Only update if the fields are currently null

            profile.setLinkedInAccessToken(linkedInAccessToken);
            profile.setLinkedInAccessTokenExpireDate(linkedInAccessTokenExpireDate);
            profile.setLinkedInPersonId(linkedInPersonId);

            // Save the updated profile
            return profileRepository.save(profile);
        } else {
            // If profile doesn't exist, create a new one
            Profile newProfile = new Profile();
            newProfile.setUserId(user.getUserid());  // Set the User object
            newProfile.setLinkedInAccessToken(linkedInAccessToken);
            newProfile.setLinkedInAccessTokenExpireDate(linkedInAccessTokenExpireDate);
            newProfile.setLinkedInPersonId(linkedInPersonId);
            // Save the new profile
            return profileRepository.save(newProfile);
        }
    }

    public String getAccessToken(int userId, String code, String state) {

        String tokenUrl = APIConstants.LINKEDIN_GET_ACCESS_TOKEN;

        // Prepare the form data
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        log.info("Code : "+code);
        body.add("code", code);
        body.add("redirect_uri", APIConstants.LINKEDIN_REDIRECT_URL);
        body.add("client_id", APIConstants.CLIENT_ID);
        body.add("client_secret", APIConstants.CLIENT_SECRET);

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // Create HTTP request entity with body and headers
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

        try {
            // Send POST request to LinkedIn to exchange code for access token
            ResponseEntity<Map> response = restTemplate.exchange(tokenUrl, HttpMethod.POST, requestEntity, Map.class);

            // Extract access token from the response
            String accessToken = (String) response.getBody().get("access_token");
            log.info("Got it : "+accessToken);

            String personId = getPersonId(accessToken);
            log.info("PersonId: "+personId);
            Profile profile=null;
            if(!personId.startsWith("{"));
            {
                log.info("updating");
                profile = updateLinkedInAccessToken(userId, accessToken, personId);
            }

            return (profile!=null)? "Success" : "Failed";

        } catch (Exception e) {
            e.printStackTrace();
            return "Failed";
        }
    }

//    public String getPersonId(String accessToken) {
//        String url = "https://api.linkedin.com/v2/userinfo";
//
//        // Set the headers
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Authorization", "Bearer " + accessToken);
//        headers.set("X-Restli-Protocol-Version", "2.0.0");
//
//        // Create an HttpEntity with the headers
//        HttpEntity<String> entity = new HttpEntity<>(headers);
//
//        try {
//            // Send the GET request with the headers and capture the response
//            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
//
//            // Parse the response body into a Map
//            ObjectMapper objectMapper = new ObjectMapper();
//            Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);
//
//            // Extract the "sub" value
//            String sub = responseBody.get("sub").toString();
//
//            return sub;
//
//        } catch (HttpClientErrorException e) {
//            // Handle error responses (e.g., 401 Unauthorized, 403 Forbidden, etc.)
//            return "Error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
//        } catch (Exception e) {
//            return "Error: " + e.getMessage();
//        }
//    }

    public String getPersonId(String accessToken) {
        String url = APIConstants.LINKEDIN_GET_PERSON_ID;

        // Set the headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("X-Restli-Protocol-Version", "2.0.0");

        // Create an HttpEntity with the headers
        HttpEntity<String> entity = new HttpEntity<>(headers);
        log.info("in PersonId method....");
        try {
            // Send the GET request with the headers and capture the response
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            // Parse the response body into a Map
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);
            log.info("Response : "+ responseBody);
            // Extract the "id" value (not "sub")
            String personId = responseBody.get("sub").toString(); // Adjust this based on the actual response

            return personId;

        } catch (HttpClientErrorException e) {
            // Log and handle specific error responses
            System.err.println("Error fetching person ID: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
