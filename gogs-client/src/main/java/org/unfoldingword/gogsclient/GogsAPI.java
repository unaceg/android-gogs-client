package org.unfoldingword.gogsclient;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

/**
 * The main gogs api controller
 */
public class GogsAPI {

    private int readTimeout = 5000;
    private int connectionTimeout = 5000;
    private final String baseUrl;
    private Response lastResponse = null;

    public GogsAPI(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "") + "/";
    }

    /**
     * Returns the last reponse from the api
     * @return
     */
    public Response getLastResponse() {
        return this.lastResponse;
    }

    /**
     * Changes the read timeout
     * @param timeout
     */
    public void setReadTimeout(int timeout) {
        this.readTimeout = timeout;
    }

    /**
     * Sets the connection timeout
     * @param timeout
     */
    public void setConnectionTimeout(int timeout) {
        this.connectionTimeout = timeout;
    }

    /**
     * Performs a request against the api
     * @param partialUrl
     * @param user
     * @param postData if not null the request will POST the data otherwise it will be a GET request
     * @return
     */
    private Response request(String partialUrl, User user, String postData) {
        return request(partialUrl, user, postData, null);
    }

    /**
     * Performs a request against the api
     * @param partialUrl
     * @param user
     * @param postData if not null the request will POST the data otherwise it will be a GET request
     * @param requestMethod if null the request method will default to POST or GET
     * @return
     */
    private Response request(String partialUrl, User user, String postData, String requestMethod) {
        int responseCode = -1;
        String responseData = null;
        Exception exception = null;
        try {
            URL url = new URL(this.baseUrl + partialUrl.replaceAll("^/+", ""));
            // TODO: 2/26/2016 we should check the url and use http(s) as needed
            HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
            if(user != null) {
                String auth = encodeUserAuth(user);
                if(auth != null) {
                    conn.addRequestProperty("Authorization", auth);
                }
            }
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setReadTimeout(this.readTimeout);
            conn.setConnectTimeout(this.connectionTimeout);

            // custom request method
            if(requestMethod != null) {
                conn.setRequestMethod(requestMethod.toUpperCase());
            }

            if(postData != null) {
                // post
                if(requestMethod == null) {
                    conn.setRequestMethod("POST");
                }
                conn.setDoOutput(true);
                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
                dos.writeBytes(postData);
                dos.flush();
                dos.close();
            }

            responseCode = conn.getResponseCode();

            if(isRequestMethodReadable(conn.getRequestMethod())) {
                // read response
                InputStream is = conn.getInputStream();
                BufferedInputStream bis = new BufferedInputStream(is);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int current;
                while ((current = bis.read()) != -1) {
                    baos.write((byte) current);
                }
                responseData = baos.toString("UTF-8");
            }
        } catch (Exception e) {
            exception = e;
        }
        this.lastResponse = new Response(responseCode, responseData, exception);
        return this.lastResponse;
    }

    /**
     * Checks if the request method is one that will return content
     * @param method
     * @return
     */
    private boolean isRequestMethodReadable(String method) {
        switch(method.toUpperCase()) {
            case "DELETE":
            case "PUT":
                return false;
            default:
                return true;
        }
    }

    /**
     * Generates the authentication parameter for the user
     * Preference will be given to the token if it exists
     * @param user
     * @return
     */
    private String encodeUserAuth(User user) {
        if(user != null) {
            if(user.getToken() != null) {
                return "token " + user.getToken();
            } else if(!user.getUsername().isEmpty() && !user.getPassword().isEmpty()) {
                String credentials = user.getUsername() + ":" + user.getPassword();
                try {
                    return "Basic " + Base64.encodeToString(credentials.getBytes("UTF-8"), Base64.NO_WRAP);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    /**
     * Creates a new user
     * username, email, and password are required
     * @param user
     * @param authUser
     * @param notify
     * @return
     */
    public User createUser(User user, User authUser, boolean notify) {
        if(user != null) {
            JSONObject json = new JSONObject();
            try {
                json.put("username", user.getUsername());
                json.put("email", user.email);
                json.put("password", user.getPassword());
                json.put("send_notify", notify);
                Response response = request("/admin/users", authUser, json.toString());
                if(response.code == 201 && response.data != null) {
                    return User.parse(new JSONObject(response.data));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Searches for users that match the query
     * @param query
     * @param limit the maximum number of results to return
     * @param user user to authenticate as. If null the email fields will be empty in the result
     * @return
     */
    public List<User> searchUsers(String query, int limit, User user) {
        List<User> users = new ArrayList<>();
        if(query != null && !query.trim().isEmpty()) {
            Response response = request(String.format("/users/search?q=%s&limit=%d", query, limit),user, null);
            if(response.code == 200 && response.data != null) {
                try {
                    JSONObject json = new JSONObject(response.data);
                    JSONArray data = json.getJSONArray("data");
                    for(int i = 0; i < data.length(); i ++) {
                        User u = User.parse(data.getJSONObject(i));
                        if(u != null) {
                            users.add(u);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return users;
    }

    /**
     * Retrieves a user
     * @param user
     * @param authUser the user to authenticate as. if null the email field in the response will be empty
     * @return
     */
    public User getUser(User user, User authUser) {
        if(user != null) {
            Response response = request(String.format("/users/%s", user.getUsername()), authUser, null);
            if(response.code == 200 && response.data != null) {
                try {
                    return User.parse(new JSONObject(response.data));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    /**
     * Deletes a user
     * @param user the user to delete. We just need the username
     * @param authUser the user to authenticate as. Users cannot delete themselves
     * @return true if the request did not encounter an error
     */
    public boolean deleteUser(User user, User authUser) {
        if(user != null && authUser != null && !user.getUsername().equals(authUser.getUsername())) {
            Response response = request(String.format("/admin/users/%s", user.getUsername()), authUser, null, "DELETE");
            if(response.code == 204) {
                return true;
            }
        }
        return false;
    }

    /**
     * Searches for public repositories that match the query
     * @param query
     * @param uid user whose repositories will be searched. 0 will search all
     * @param limit limit results to this quantity.
     * @return
     */
    public List<Repository> searchRepos(String query, int uid, int limit) {
        List<Repository> repos = new ArrayList<>();
        if(query != null && !query.trim().isEmpty()) {
            Response response = request(String.format("/repos/search?q=%s&uid=%d&limit=%d", query.trim(), uid, limit), null, null);
            if(response.code == 200 && response.data != null) {
                try {
                    JSONObject json = new JSONObject(response.data);
                    JSONArray data = json.getJSONArray("data");
                    for(int i = 0; i < data.length(); i ++) {
                        Repository repo = Repository.parse(data.getJSONObject(i));
                        if(repo != null) {
                            repos.add(repo);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return repos;
    }

    /**
     * Lists all repositories that are accessible to the user
     * @param user
     * @return
     */
    public List<Repository> listRepos(User user) {
        List<Repository> repos = new ArrayList<>();
        if(user != null) {
            Response response = request("/user/repos", user, null);
            if(response.code == 200 && response.data != null) {
                try {
                    JSONArray data = new JSONArray(response.data);
                    for(int i = 0; i < data.length(); i ++) {
                        Repository repo = Repository.parse(data.getJSONObject(i));
                        if(repo != null) {
                            repos.add(repo);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return repos;
    }

    /**
     * Creates a new repository for the user
     * @param repo
     * @param user
     * @return
     */
    public Repository createRepo(Repository repo, User user) {
        if(repo != null && user != null) {
            JSONObject json = new JSONObject();
            try {
                json.put("name", repo.getName());
                json.put("description", repo.getDescription());
                json.put("private", repo.getIsPrivate());
                Response response = request("/user/repos", user, json.toString());
                if(response.code == 201 && response.data != null) {
                    return Repository.parse(new JSONObject(response.data));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Deletes a repository from the user
     * @param repo
     * @param user
     * @return true if the request did not encounter an error
     */
    public boolean deleteRepo(Repository repo, User user) {
        if(repo != null && user != null) {
            Response response = request(String.format("/repos/%s/%s", user.getUsername(), repo.getName()), user, null, "DELETE");
            if(response.code == 204) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a list of tokens the user has
     * @param user
     * @return
     */
    public List<Token> listTokens(User user) {
        List<Token> tokens = new ArrayList<>();
        if(user != null) {
            Response response = request(String.format("/users/%s/tokens", user.getUsername()), user, null);
            if(response.code == 200 && response.data != null) {
                try {
                    JSONArray data = new JSONArray(response.data);
                    for(int i = 0; i < data.length(); i ++) {
                        Token token = Token.parse(data.getJSONObject(i));
                        if(token != null) {
                            tokens.add(token);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return tokens;
    }

    /**
     * Creates an authentication token for the user
     * @param token
     * @param user
     * @return
     */
    public Token createToken(Token token, User user) {
        if(token != null && user != null) {
            JSONObject json = new JSONObject();
            try {
                json.put("name", token.getName());
                Response response = request(String.format("/users/%s/tokens", user.getUsername()), user, json.toString());
                if(response.code == 201 && response.data != null) {
                    return Token.parse(new JSONObject(response.data));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Lists the public keys the user has
     * @param user
     * @return
     */
    public List<PublicKey> listPublicKeys(User user) {
        List<PublicKey> keys = new ArrayList<>();
        if(user != null) {
            Response response = request(String.format("/users/%s/keys", user.getUsername()), user, null);
            if(response.code == 200 && response.data != null) {
                try {
                    JSONArray data = new JSONArray(response.data);
                    for(int i = 0; i < data.length(); i ++) {
                        PublicKey key = PublicKey.parse(data.getJSONObject(i));
                        if(key != null) {
                            keys.add(key);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return keys;
    }

    /**
     * Creates a public key for the user
     * @param key the key to be created
     * @param user
     * @return
     */
    public PublicKey createPublicKey(PublicKey key, User user) {
        if(key != null && user != null) {
            JSONObject json = new JSONObject();
            try {
                json.put("title", key.getTitle());
                json.put("key", key.getKey());
                Response response = request("/user/keys", user, json.toString());
                if (response.code == 201 && response.data != null) {
                    return PublicKey.parse(new JSONObject(response.data));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Returns the full details for a public key
     * @param key the key that will be retrieved. We need the id
     * @param user
     * @return
     */
    public PublicKey getPublicKey(PublicKey key, User user) {
        if(key != null && user != null) {
            Response response = request(String.format("/user/keys/%d", key.getId()), user, null);
            if(response.code == 200 && response.data != null) {
                try {
                    return PublicKey.parse(new JSONObject(response.data));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    /**
     * Deletes a public key from the user
     * @param key the key that will be deleted. We need the id
     * @param user
     * @return
     */
    public boolean deletePublicKey(PublicKey key, User user) {
        if(key != null && user != null) {
            Response response = request(String.format("/user/keys/%s", key.getId()), user, null, "DELETE");
            if(response.code == 204) {
                return true;
            }
        }
        return false;
    }
}
