package com.tutorial.keycloakbackend.service;


import com.tutorial.keycloakbackend.dto.ResponseMessage;
import com.tutorial.keycloakbackend.dto.UserDataOnly;
import com.tutorial.keycloakbackend.model.User;
import com.tutorial.keycloakbackend.repository.UserRepository;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.Response;
import java.util.Arrays;

@Service
public class KeycloakService {
    // == fields ==
    @Value("${keycloak.auth-server-url}")
    private String server_url;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${app.master.realm}")
    private String master_realm;

    @Value("${app.master.username}")
    private String master_username;

    @Value("${app.master.password}")
    private String master_admin;

    @Value("${app.master.clientId}")
    private String master_clientId;

    private UserRepository userRepository;

    // == constructors ==
    @Autowired
    private KeycloakService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Object[] createUser(UserDataOnly userDataOnly){
        ResponseMessage message = new ResponseMessage();
        int statusId = 0;
         try {
             UsersResource usersResource = getUsersResource();
             UserRepresentation userRepresentation = new UserRepresentation();
             userRepresentation.setUsername(userDataOnly.getUsername());
             userRepresentation.setEmail(userDataOnly.getEmail());
             userRepresentation.setFirstName(userDataOnly.getFirstName());
             userRepresentation.setLastName(userDataOnly.getLastName());
             userRepresentation.setEnabled(true);

             Response result = usersResource.create(userRepresentation);
             statusId = result.getStatus();

             if(statusId == 201){
                 String path = result.getLocation().getPath();
                 String userId = path.substring(path.lastIndexOf("/") + 1);
                 CredentialRepresentation passwordCredential = new CredentialRepresentation();
                 passwordCredential.setTemporary(false);
                 passwordCredential.setType(CredentialRepresentation.PASSWORD);
                 passwordCredential.setValue(userDataOnly.getPassword());
                 usersResource.get(userId).resetPassword(passwordCredential);

                 RealmResource realmResource = getRealmResource();
                 RoleRepresentation roleRepresentation = realmResource.roles().get("realm-user").toRepresentation();
                 realmResource.users().get(userId).roles().realmLevel().add(Arrays.asList(roleRepresentation));
                 // Create User in the platform db
                 userRepository.save(new User(userDataOnly.getUsername()));
                 message.setMessage("usuario creado con éxito");
             }else if(statusId == 409){
                 message.setMessage("ese usuario ya existe");
             }else{
                 message.setMessage("error creando el usuario");
             }
         }catch (Exception e){
             e.printStackTrace();
         }

         return new Object[]{statusId, message};
    }

    /**
     * Here you have to enter the username and password of the realm administrator.
     *
     * @return
     */
    private RealmResource getRealmResource(){
        Keycloak kc = KeycloakBuilder.builder().serverUrl(server_url).realm(master_realm).username(master_username)
                .password(master_admin).clientId(master_clientId).resteasyClient(new ResteasyClientBuilder().connectionPoolSize(10).build())
                .build();
        return kc.realm(realm);
    }

    private UsersResource getUsersResource(){
        RealmResource realmResource = getRealmResource();
        return realmResource.users();
    }
}
