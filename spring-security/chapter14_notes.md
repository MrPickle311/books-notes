# Chapter 14: Implementing an OAuth 2 authorization server

## Overview
The Spring Security authorization server framework is the de facto standard for implementing an OAuth 2/OpenID Connect authorization server in Spring today. Keep in mind that the current approach is entirely different from older implementations (such as those prior to Spring Authorization Server), so it is important to understand the new components.

This chapter focuses on:
- Implementing an authorization server using the authorization code and client credentials grant types.
- Configuring opaque and non-opaque (JWT) access tokens.
- Setting up token revocation and introspection.

As illustrated in Figure 14.1, the authorization server plays a crucial role in the OAuth 2 architecture. It protects user and client details while issuing tokens that the client can use to gain authorization when calling the resource server endpoints.
![Actors in the OAuth 2 scene](figures/figure-14-1.png)

## 14.1 Implementing Basic Authentication using JSON Web Tokens
To set up a basic OAuth 2 authorization server using the Spring Security framework, several main components must be configured. Figure 14.2 provides a visual representation of these components and how they plug into the authorization server to make it work.
![Components for an authorization server](figures/figure-14-2.png)

These components are:
1. **Configuration filter for protocol endpoints**: Helps define configurations specific to the authorization server capabilities, such as OpenID Connect.
2. **Authentication configuration filter**: Similar to any standard web application secured with Spring Security, this filter defines general authentication and authorization configurations (e.g., form login).
3. **User details management**: Manages user authentication through `UserDetailsService` and `PasswordEncoder` beans.
4. **Client details management**: The authorization server manages client credentials and details using a `RegisteredClientRepository`.
5. **Key-pairs management (JWKSource)**: When using non-opaque tokens (like JWTs), the server signs them with a private key. The public key is made available for resource servers to validate these tokens. This is handled by a "key source" component.
6. **General app settings**: Configured via an `AuthorizationServerSettings` bean to customize generic settings, such as endpoint paths.

### Dependencies
To begin, you need to add the following dependencies to your project:
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
</dependency>
```

### Configuration
#### Protocol Endpoints Filter
The `applyDefaultSecurity()` method provides a minimal set of default endpoint configurations that you can override later. Here we also enable OpenID Connect and configure the default authentication entry point.
```java
@Bean
@Order(1)
public SecurityFilterChain asFilterChain(HttpSecurity http) throws Exception {
    OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
    http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
        .oidc(Customizer.withDefaults()); // Enable OpenID Connect
    http.exceptionHandling((e) ->
        e.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
    );
    return http.build();
}
```

#### Authorization Configuration Filter
We set up a standard filter chain (with `@Order(2)`) to handle typical web app security mechanisms, ensuring that all endpoints require authentication and enabling form login.
```java
@Bean
@Order(2)
public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
    http.formLogin(Customizer.withDefaults());
    http.authorizeHttpRequests(c -> c.anyRequest().authenticated());
    return http.build();
}
```

#### User Details Management
Just like any standard Spring Security application, the authorization server requires a `UserDetailsService` to authenticate users when a flow (like the authorization code grant) requires user consent.
```java
@Bean
public UserDetailsService userDetailsService() {
    UserDetails userDetails = User.withUsername("bill")
        .password("password")
        .roles("USER")
        .build();
    return new InMemoryUserDetailsManager(userDetails);
}

@Bean
public PasswordEncoder passwordEncoder() {
    return NoOpPasswordEncoder.getInstance(); // For learning only; use a strong hasher like BCrypt in prod
}
```

#### Client Details Management
Similar to how `UserDetailsService` manages users, the authorization server requires a `RegisteredClientRepository` component to manage client details. As shown in Figure 14.3, the `RegisteredClientRepository` uses `RegisteredClient` objects to represent the apps allowed to request tokens.
![RegisteredClientRepository](figures/figure-14-3.png)

```java
@Bean
public RegisteredClientRepository registeredClientRepository() {
    RegisteredClient registeredClient = RegisteredClient
        .withId(UUID.randomUUID().toString())
        .clientId("client")
        .clientSecret("secret")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("https://www.manning.com/authorized")
        .scope(OidcScopes.OPENID)
        .build();
    return new InMemoryRegisteredClientRepository(registeredClient);
}
```
**Client Registration Details:**
- **Unique internal ID**: Identifies the client internally.
- **Client ID**: The external client identifier (analogous to a username).
- **Client secret**: The client password.
- **Client authentication method**: Tells the server how the client will authenticate when requesting an access token.
- **Authorization grant type**: The allowed grant flows for this client (e.g., `AUTHORIZATION_CODE`, `CLIENT_CREDENTIALS`). A client can support multiple grant types.
- **Redirect URI**: Allowed redirect URIs after successful authorization.
- **Scope**: Defines the purpose for the access token request, which can later be used in authorization rules.

#### Key Pair Management (JWKSource)
For non-opaque tokens (like JWTs), the server uses a private key to cryptographically sign the token, and clients/resource servers use the public key to validate its authenticity. A `JWKSource` object manages these keys in the Spring Security authorization server.

> **TLS/SSL Certificates vs. JWK Key Pairs**
> A common point of confusion is how these keys differ from the `.key` or `.pem` certificate files used for HTTPS:
> - **TLS/SSL Certificates (.key, .crt):** These operate at the **network layer**. They are used by the web server (like Tomcat or Nginx) to encrypt the physical HTTPS traffic traveling between the client and the server. 
> - **JWK Key Pairs (JSON Web Keys):** These operate at the **application layer**. They have nothing to do with HTTPS. They are used exclusively by the Authorization Server to digitally "sign" the JWT payload, proving the token wasn't tampered with. Resource servers download the public JWK from the authorization server to verify the token's signature, not to encrypt traffic.

```java
@Bean
public JWKSource<SecurityContext> jwkSource() throws NoSuchAlgorithmException {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();

    RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
    RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

    RSAKey rsaKey = new RSAKey.Builder(publicKey)
        .privateKey(privateKey)
        .keyID(UUID.randomUUID().toString())
        .build();
    JWKSet jwkSet = new JWKSet(rsaKey);
    return new ImmutableJWKSet<>(jwkSet);
}
```
*Note: In production, keys should be read from a secure keystore rather than being randomly generated on restart. Otherwise, previously issued tokens will instantly fail validation if the app reboots.*

#### Authorization Server Settings
Finally, you can customize the endpoint paths the server exposes via `AuthorizationServerSettings`. Using the default builder provides standard endpoint names.
```java
@Bean
public AuthorizationServerSettings authorizationServerSettings() {
    return AuthorizationServerSettings.builder().build();
}
```

---

## 14.2 Running the Authorization Code Grant Type

**How it works:**
1. The client redirects the user to the `authorization_endpoint`.
2. The user authenticates with their credentials on the authorization server.
3. Upon successful authentication, the server redirects back to the client's `redirect_uri` along with an authorization code.
4. The client then exchanges this authorization code for an access token at the `token_endpoint`.

**When to use:**
This flow is ideal when a client application is acting on behalf of a user and requires them to explicitly grant permission and authenticate.

To see how this operates, you can first check the default endpoints by making a GET request to `http://localhost:8080/.well-known/openid-configuration`.

As shown in Figure 14.4, the client initiates the authorization code grant flow by requesting an authorization code.
![Authorization code grant type](figures/figure-14-4.png)

When the user is redirected to the authorization endpoint, they are presented with a login page to authenticate, as depicted in Figure 14.5.
![Login page](figures/figure-14-5.png)

After successfully authenticating, the authorization server redirects the user back to the client with the newly issued authorization code, which can be seen in Figure 14.6.
![Redirect URI and authorization code](figures/figure-14-6.png)

### Authorization Request Example:
```text
http://localhost:8080/oauth2/authorize?
response_type=code&
client_id=client&
scope=openid&
redirect_uri=https://www.manning.com/authorized&
code_challenge=QYPAZ5NU8yvtlQ9erXrUYR-T5AGCjCF47vN-KsaI2A8&
code_challenge_method=S256
```
*Note:* Proof Key for Code Exchange (PKCE) is enabled by default and recommended. The client must generate a random 32-byte `code_verifier`, hash it using SHA-256 to create a `code_challenge`, and send it in the authorization request. The raw verifier is then sent during the token exchange to prove the client's identity.

### Token Request Example (cURL):
```bash
curl -X POST 'http://localhost:8080/oauth2/token?
client_id=client&
redirect_uri=https://www.manning.com/authorized&
grant_type=authorization_code&
code=ao2oz47zdM0D5gbAqtZVB…&
code_verifier=qPsH306-…' \
--header 'Authorization: Basic Y2xpZW50OnNlY3JldA=='
```

---

## 14.3 Running the Client Credentials Grant Type

**How it works:**
The client authenticates with the authorization server using its own credentials (client ID and secret) directly to obtain an access token. There is no user authentication involved in this flow.

**When to use:**
This is useful for machine-to-machine communication, background processes, or when an application acts entirely on its own behalf without an active user session. Preferably, you should register separate clients for user-dependent and user-independent grant types to easily distinguish token scope and usage.

Figure 14.7 illustrates the client credentials grant flow, highlighting that the client relies solely on its own credentials to obtain the access token.
![Client credentials grant type](figures/figure-14-7.png)

### Client Configuration Update:
```java
@Bean
public RegisteredClientRepository registeredClientRepository() {
    RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("client")
        .clientSecret("secret")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .scope("CUSTOM")
        .build();
    return new InMemoryRegisteredClientRepository(registeredClient);
}
```

### Token Request Example (cURL):
```bash
curl -X POST 'http://localhost:8080/oauth2/token?
grant_type=client_credentials&
scope=CUSTOM' \
--header 'Authorization: Basic Y2xpZW50OnNlY3JldA=='
```

---

## 14.4 Using Opaque Tokens and Introspection

**Opaque Tokens vs. Non-Opaque (JWT) Tokens:**
- **Non-Opaque (JWT):** Contains encoded claims (data) that the authorization server signs. Resource servers can validate them locally using the authorization server's public key without making a network call.
- **Opaque Tokens:** Short, random strings that carry no embedded data. To validate an opaque token and retrieve its details, the resource server MUST call the authorization server's `introspection_endpoint`.

**When to use Opaque Tokens:**
Opaque tokens are valuable when token payload size is a concern or when immediate token revocation is strictly required (since JWTs are stateless and cannot be invalidated easily before their expiration time).

Figure 14.8 visualizes the token introspection process, showing how a resource server must send opaque tokens back to the authorization server to verify their validity and details.
![Token introspection](figures/figure-14-8.png)

### Configuring a Client to Receive Opaque Tokens
```java
RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
    // ... basic config
    .tokenSettings(TokenSettings.builder()
        .accessTokenFormat(OAuth2TokenFormat.REFERENCE) // Specify opaque token format
        .build())
    .build();
```

### Introspection Request Example:
The resource server checks the token by hitting the introspection endpoint:
```bash
curl -X POST 'http://localhost:8080/oauth2/introspect?token=iED8-…' \
--header 'Authorization: Basic Y2xpZW50OnNlY3JldA=='
```
A valid token will return an `"active": true` response along with its details.

---

## 14.5 Revoking Tokens

Tokens can be explicitly invalidated before their standard expiration time using the revocation endpoint.

**How it works:**
The client sends a POST request with the access token and its credentials to the authorization server's revocation endpoint. Once revoked, any subsequent introspection calls for that token will return `"active": false`.

**When to use:**
Revocation is important in cases of security breaches, token theft, or when a user logs out explicitly and security dictates the token should immediately become invalid everywhere.

### Revocation Request Example:
```bash
curl -X POST 'http://localhost:8080/oauth2/revoke?token=N7BruErWm-44-…' \
--header 'Authorization: Basic Y2xpZW50OnNlY3JldA=='
```

*Note:* Using token revocation effectively forces the resource server to introspect every request (even for non-opaque tokens) to see if the token was revoked. This adds significant network overhead. Always evaluate whether the extra security layer is necessary or if a short token lifespan is sufficient protection.
