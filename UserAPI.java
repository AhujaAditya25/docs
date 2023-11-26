package com.sv.user.api.v1;

import static com.sv.constant.APPConstant.API_V1;
import static com.sv.constant.Message.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sv.dto.HistoryDTO;
import com.sv.dto.ResponseBean;
import com.sv.dto.EmailAggregateDTO;
import com.sv.dto.EmailDetailsDTO;
import com.sv.dto.UserDto;
import com.sv.exception.CurrentAndNewPasswordEqualException;
import com.sv.exception.DataNotFoundException;
import com.sv.exception.DuplicateRecordException;
import com.sv.exception.EventPublisherExecption;
import com.sv.exception.InvalidCurrentPasswordException;
import com.sv.exception.InvalidDataException;
import com.sv.exception.LDAPException;
import com.sv.exception.PersistenceException;
import com.sv.exception.RecordNotDataException;
import com.sv.exception.UserUsedInOpenTicketException;
import com.sv.user.dto.ChangePasswordDTO;
import com.sv.user.dto.GroupDTO;
import com.sv.user.dto.LoginInfoDTO;
import com.sv.user.dto.PasswordExpiredDTO;
import com.sv.user.entity.FeatureDetails;
import com.sv.user.entity.GroupDefinition;

import com.sv.user.entity.RoleDetails;
import com.sv.user.entity.TenantInfo;
import com.sv.user.entity.User;
import com.sv.user.entity.UserGroup;
import com.sv.user.entity.UserQuestionAnswers;
import com.sv.user.entity.UserTenantMapping;
import com.sv.user.service.IFeatureDetailsService;
import com.sv.user.service.IRoleService;
import com.sv.user.service.IGroupService;
import com.sv.user.service.IUserHistoryService;
import com.sv.user.service.IUserService;
import com.sv.user.service.IUserTenantMappingService;

@RestController
@Scope(value = "request")
@RequestMapping(API_V1)
@CrossOrigin("*")
public class UserAPI {

	@Autowired
	private IUserService userSvc;

	@Autowired
	private IGroupService grpSvc;

	@Autowired
	private IUserHistoryService userHistorySvc;

	@Autowired
	private IRoleService roleSvc;

	@Autowired
	private IUserTenantMappingService userTenantMappingSvc;
	
	@Autowired
	private IFeatureDetailsService featureDetailsService;

	@GetMapping("/role/{level}")
	public ResponseEntity<ResponseBean<List<RoleDetails> >> findRolesByLevel(@PathVariable("level") int level) throws DataNotFoundException, InvalidDataException {
		return ResponseEntity.ok(new ResponseBean<List<RoleDetails>>(ROLE_FOUND, roleSvc.findRolesByLevel(level)));
	}
	
	@GetMapping("/role/")
	public ResponseEntity<ResponseBean<List<RoleDetails> >> findAllRoles() throws DataNotFoundException, InvalidDataException {
		return ResponseEntity.ok(new ResponseBean<List<RoleDetails>>(ROLE_FOUND, roleSvc.findByActive(true)));
	}

	@GetMapping("/role/id/{id}")
	public ResponseEntity<ResponseBean<RoleDetails>> findById(@PathVariable("id") String id) throws DataNotFoundException, InvalidDataException {
		return ResponseEntity.ok(new ResponseBean<RoleDetails>(ROLE_FOUND, roleSvc.findById(id)));
	}

	@PostMapping("/role/")
	public ResponseEntity<ResponseBean<RoleDetails>> saveRole(@RequestBody RoleDetails roleDetails) throws InvalidDataException, PersistenceException {
		return ResponseEntity.ok(new ResponseBean<RoleDetails>(ROLE_FOUND, roleSvc.saveRole(roleDetails)));
	}

	@PutMapping("/role/{id}")
	public ResponseEntity<ResponseBean<RoleDetails>> updateRole(@PathVariable("id") String id, @RequestBody RoleDetails roleDetails) throws InvalidDataException, PersistenceException {
		return ResponseEntity.ok(new ResponseBean<RoleDetails>(ROLE_FOUND, roleSvc.updateRole(id,roleDetails)));
	}

	@GetMapping("/group/{type}/{entityId}")
	public ResponseEntity<ResponseBean<List<GroupDefinition>>> findGroupsByTypeAndEntityId(@PathVariable("type") int type,@PathVariable("entityId") String entityId) throws InvalidDataException, PersistenceException, DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<List<GroupDefinition>>(ROLE_FOUND, grpSvc.findGroupsByTypeAndEntityId(type,entityId)));
	}

	@PostMapping("/groups/")
	public ResponseEntity<ResponseBean<Document>> findGroupsByEntities(@RequestBody List<String> entities) throws InvalidDataException, PersistenceException, DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<Document>(ALL_GROUP_FOUND, grpSvc.findGroupsByEntities(entities)));
	}

	
	@GetMapping("/group/{entityId}")
	public ResponseEntity<ResponseBean<List<GroupDefinition>>> findGroupsByEntityId(@PathVariable("entityId") String entityId) throws InvalidDataException, PersistenceException, DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<List<GroupDefinition>>(ROLE_FOUND, grpSvc.findGroupsByEntityId(entityId)));
	}

	@GetMapping("/group/{entityId}/with/mssp")
	public ResponseEntity<ResponseBean<List<GroupDefinition>>> findGroupsByEntityIdWithMssp(@PathVariable("entityId") String entityId) throws InvalidDataException, PersistenceException, DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<List<GroupDefinition>>(ROLE_FOUND, grpSvc.findGroupsByEntityIdWithMssp(entityId)));
	}

	@PostMapping("/group/")
	public ResponseEntity<ResponseBean<GroupDefinition>> saveGroup(@RequestBody GroupDefinition _grp) throws InvalidDataException, PersistenceException {
		return ResponseEntity.ok(new ResponseBean<GroupDefinition>(RECORD_SAVED, grpSvc.save(_grp)));
	}

	@PutMapping("/group/{id}")
	public ResponseEntity<ResponseBean<GroupDefinition>> updateGroup(@PathVariable("id") String id, @RequestBody GroupDefinition _grp) throws InvalidDataException, PersistenceException, DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<GroupDefinition>(RECORD_UPDATED, grpSvc.update(id, _grp)));
	}

	@DeleteMapping("/group/{id}")
	public ResponseEntity<ResponseBean<String>> deleteGroup(@PathVariable("id") String id) throws InvalidDataException, PersistenceException, DataNotFoundException {
		grpSvc.delete(id);
		return ResponseEntity.ok(new ResponseBean<String>(RECORD_DELETED, "Group deleted"));
	}

	@GetMapping("/usergroup/{id}/{type}/{grpid}")
	public ResponseEntity<ResponseBean<UserGroup>> getUserGroups(@PathVariable("id") String id, @PathVariable("type") String type, @PathVariable("grpid") String grpId) throws InvalidDataException, PersistenceException, DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<UserGroup>(ROLE_FOUND, grpSvc.findByGroupIdAndEntityIdAndEntityTypeAndActive(id, type, grpId)));
	}

	@PostMapping("/usergroup/")
	public ResponseEntity<ResponseBean<UserGroup>> saveUserGroup(@RequestBody UserGroup _userGroup) throws InvalidDataException, PersistenceException {
		return ResponseEntity.ok(new ResponseBean<UserGroup>(ROLE_FOUND, grpSvc.save(_userGroup)));
	}
	
	@PostMapping("/usergroup/emails")
	public ResponseEntity<ResponseBean<List<String>>> getuserEmails(@RequestBody List<String> grpIds) throws InvalidDataException, PersistenceException {
		return ResponseEntity.ok(new ResponseBean<List<String>>(ROLE_FOUND, grpSvc.groupEmails(grpIds)));
	}
 

	@GetMapping("/login/info")
	public ResponseEntity<ResponseBean<LoginInfoDTO>> userLoginDetails() throws DataNotFoundException, InvalidDataException {
		return ResponseEntity.ok(new ResponseBean<LoginInfoDTO>(ALL_USERS_FOUND, userSvc.userLoginDetails()));
	}

	@GetMapping("/users/{type}/{referenceId}")
	public ResponseEntity<ResponseBean<List<User>>> getAllUsers(@PathVariable("type") String type, @PathVariable("referenceId") String referenceId) throws DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<List<User>>(ALL_USERS_FOUND, userSvc.getAllUsers(type, referenceId)));
	}
	
	@GetMapping("/enabledusers/{type}/{referenceId}")
	public ResponseEntity<ResponseBean<List<User>>> getAllEnabledUsers(@PathVariable("type") String type, @PathVariable("referenceId") String referenceId) throws DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<List<User>>(ALL_USERS_FOUND, userSvc.getAllEnabledUsers(type, referenceId)));
	}
	
	@GetMapping("/users/{referenceId}/all")
	public ResponseEntity<ResponseBean<List<User>>> getAllUser(@PathVariable("referenceId") String referenceId) throws DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<List<User>>(ALL_USERS_FOUND, userSvc.getAllUser(referenceId)));
	}
	
	@GetMapping("/users/{referenceId}/mspandpartner")
	public ResponseEntity<ResponseBean<List<User>>> getMspAndPartnerUser(@PathVariable("referenceId") String referenceId) throws DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<List<User>>(ALL_USERS_FOUND, userSvc.getMspAndPartnerUser(referenceId)));
	}

	@GetMapping("/users/{type}/")
	public ResponseEntity<ResponseBean<List<User>>> getAllUsers(@PathVariable("type") String type) throws DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<List<User>>(ALL_USERS_FOUND, userSvc.getAllUsers(type)));
	}

	@PostMapping("/users/")
	public ResponseEntity<ResponseBean<List<User>>> getUsers(@RequestBody List<String> userIds) throws DataNotFoundException, InvalidDataException {
		List<User> users = new ArrayList<User>();
		for (String id : userIds) {
			User user =  userSvc.findByUserId(id);
			users.add(user);
		}
		return ResponseEntity.ok(new ResponseBean<List<User>>(ALL_USERS_FOUND, users));
	}
	
	@GetMapping("/users/{id}")
	public ResponseEntity<ResponseBean<User>> findByUserId(@PathVariable("id") String id) throws DataNotFoundException, InvalidDataException {
		return ResponseEntity.ok(new ResponseBean<User>(ALL_USERS_FOUND, userSvc.findByUserId(id)));
	}

	@GetMapping("/users/{entityId}/entity")
	public ResponseEntity<ResponseBean<List<User>>> getAllUsersByEntityId(@PathVariable("entityId") String entityId) throws DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<List<User>>(ALL_USERS_FOUND, userSvc.getAllUsersByEntityId(entityId)));
	}

	//----------------------------------------
	@GetMapping("/users/{id}/resetpassword")
	public ResponseEntity<ResponseBean<Boolean>> resetPassword(@PathVariable("id") String id) throws DataNotFoundException, InvalidDataException, PersistenceException, EventPublisherExecption {
		return ResponseEntity.ok(new ResponseBean<Boolean>(ALL_USERS_FOUND, userSvc.resetPassword(id)));
	}
	
	@GetMapping("/users/{id}/sendwelcomemail")
	public ResponseEntity<ResponseBean<Boolean>> sendWelcomeMail(@PathVariable("id") String id) throws DataNotFoundException, InvalidDataException, PersistenceException, EventPublisherExecption {
		return ResponseEntity.ok(new ResponseBean<Boolean>(WELCOME_EMAIL_SEND, userSvc.sendWelcomeMail(id)));
	}
	
	@PostMapping("/user/changepassword")
	public ResponseEntity<ResponseBean<Boolean>> changePassword(@RequestBody ChangePasswordDTO dto) throws DataNotFoundException, InvalidDataException, PersistenceException, EventPublisherExecption,InvalidCurrentPasswordException,CurrentAndNewPasswordEqualException {
		return ResponseEntity.ok(new ResponseBean<Boolean>(ALL_USERS_FOUND, userSvc.changePassword(dto)));
	}
	
	//------------------------------------------------------------
	@GetMapping("/users/{id}/password/expiry")
	public ResponseEntity<ResponseBean<PasswordExpiredDTO>> checkPasswordExpiry(@PathVariable("id") String id) throws DataNotFoundException, InvalidDataException, PersistenceException, EventPublisherExecption {
		return ResponseEntity.ok(new ResponseBean<PasswordExpiredDTO>("Password Expiry API call successful ! ", userSvc.checkPasswordExpiry(id)));
	}
	
	@GetMapping("/users/{id}/history/")
	public ResponseEntity<ResponseBean<List<HistoryDTO>>> findHistoryByUserId(@PathVariable("id") String id) throws DataNotFoundException, InvalidDataException {
		return ResponseEntity.ok(new ResponseBean<List<HistoryDTO>>(ALL_USERS_FOUND, userHistorySvc.findHistoryByUserId(id)));
	}

	@DeleteMapping("/users/{id}")
	public ResponseEntity<ResponseBean<String>> deleteUser(@PathVariable("id") String id) throws DataNotFoundException, InvalidDataException, PersistenceException {
		return ResponseEntity.ok(new ResponseBean<String>(RECORD_DELETED, userSvc.deleteUser(id)));
	}

	@PostMapping("/users/{type}/{referenceId}")
	public ResponseEntity<ResponseBean<User>> createUser(@PathVariable("type") String type, @PathVariable("referenceId") String referenceId, @RequestBody User user) throws  InvalidDataException, PersistenceException, DuplicateRecordException {
		return ResponseEntity.ok(new ResponseBean<User>(RECORD_SAVED, userSvc.createUser(type, referenceId, user)));
	}

	@PutMapping("/users/{id}/")
	public ResponseEntity<ResponseBean<User>> updateUser(@PathVariable("id") String id,  @RequestBody User user) throws  InvalidDataException, PersistenceException, DataNotFoundException, DuplicateRecordException, UserUsedInOpenTicketException {
		return ResponseEntity.ok(new ResponseBean<User>(RECORD_UPDATED, userSvc.updateUser(id,user)));
	}
	
	@PutMapping("/users/timezone/{id}")
	public ResponseEntity<ResponseBean<User>> updateUserTimezone(@PathVariable("id") String id,  @RequestBody User userTz) throws  InvalidDataException, PersistenceException, DataNotFoundException, DuplicateRecordException {
		return ResponseEntity.ok(new ResponseBean<User>(RECORD_UPDATED, userSvc.updateUserTimezone(id,userTz)));
	}

	@GetMapping("/user/tenant/mapping")
	public ResponseEntity<ResponseBean<List<TenantInfo>>> fetchUserTenantMappingCustomers() throws  InvalidDataException, DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<List<TenantInfo>>(RECORD_SAVED, userTenantMappingSvc.fetchUserTenant()));
	}

	@PostMapping("/user/tenant/mapping")
	public ResponseEntity<ResponseBean<UserTenantMapping>> createUserTenantMapping(@RequestBody UserTenantMapping entity) throws  InvalidDataException, PersistenceException {
		return ResponseEntity.ok(new ResponseBean<UserTenantMapping>(RECORD_SAVED, userTenantMappingSvc.save(entity)));
	}

	@GetMapping("/user/{id}/tenant/mapping")
	public ResponseEntity<ResponseBean<UserTenantMapping>> fetchUserTenantMapping(@PathVariable("id") String id) throws  InvalidDataException, DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<UserTenantMapping>(RECORD_SAVED, userTenantMappingSvc.fetchUserTenantMapping(id)));
	}

	@GetMapping("/user/customers/")
	public ResponseEntity<ResponseBean<List<TenantInfo>>> fetchUserCustomers() throws  InvalidDataException, DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<List<TenantInfo>>(RECORD_SAVED, userTenantMappingSvc.fetchUserCustomers()));
	}

	@GetMapping("/users/{userid}/tenantcontext")
	public ResponseEntity<ResponseBean<LoginInfoDTO>> fetchUserSessionContext(@PathVariable("userid") String userid) throws  InvalidDataException, DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<LoginInfoDTO>(RECORD_SAVED, userSvc.fetchUserSessionContext(userid)));
	}
	
	@GetMapping("/features/")
	public ResponseEntity<ResponseBean<List<FeatureDetails> >> findAllFeatures() throws DataNotFoundException {
		return ResponseEntity.ok(new ResponseBean<List<FeatureDetails>>(ROLE_FOUND, featureDetailsService.fetchAllFeatures()));
	}

	@PostMapping("/feature/")
	public ResponseEntity<ResponseBean<FeatureDetails>> saveFeature(@RequestBody FeatureDetails featureDetails) throws InvalidDataException, PersistenceException {
		return ResponseEntity.ok(new ResponseBean<FeatureDetails>(ROLE_FOUND, featureDetailsService.save(featureDetails)));
	}

	@GetMapping("/questionanswers/")
	public ResponseEntity<ResponseBean<UserQuestionAnswers>> getUserQuestions() throws RecordNotDataException {
		return ResponseEntity.ok(new ResponseBean<UserQuestionAnswers>(ALL_USERS_QUESTION_FOUND, userSvc.getUserQuestions()));
	}

	@PostMapping("/questionanswers/")
	public ResponseEntity<ResponseBean<UserQuestionAnswers>> saveQuestionsAns(@RequestBody UserQuestionAnswers userQuestionAnswers) throws InvalidDataException, PersistenceException, DuplicateRecordException {
		return ResponseEntity.ok(new ResponseBean<UserQuestionAnswers>(RECORD_SAVED, userSvc.saveUserQuestionsAnswers(userQuestionAnswers)));
	}
	
	@GetMapping("/questions/{loginId}")
	public ResponseEntity<ResponseBean<UserQuestionAnswers>> findQuestionByUserId(@PathVariable("loginId") String loginId) throws DataNotFoundException, InvalidDataException {
		return ResponseEntity.ok(new ResponseBean<UserQuestionAnswers>(ROLE_FOUND, userSvc.findUserByUserId(loginId, true)));
	}

	@GetMapping("/userquestionanswers/")
	public ResponseEntity<ResponseBean<UserQuestionAnswers>> findQuestionAnswerByUserId() throws DataNotFoundException, InvalidDataException {
		return ResponseEntity.ok(new ResponseBean<UserQuestionAnswers>(ROLE_FOUND, userSvc.findUserQuestionByLoginId()));
	}

	//------------------------------------------------
	@PostMapping("/resetpasword/") 
	public ResponseEntity<ResponseBean<UserQuestionAnswers>> forgotPassword(@RequestBody UserQuestionAnswers userQuestionAnswers) throws InvalidDataException, DataNotFoundException, PersistenceException, EventPublisherExecption {
		if(userSvc.checkUserQuestionsAnswers(userQuestionAnswers)) {
			return ResponseEntity.ok(new ResponseBean<UserQuestionAnswers>(true,DATA_MATCH));
		}else {
			return ResponseEntity.ok(new ResponseBean<UserQuestionAnswers>(false,DATA_NOT_MATCH));
		} 
	 }
	
	@GetMapping("/create/ad/user")
	public ResponseEntity<ResponseBean<Boolean>> createUsersInAD() throws DataNotFoundException, InvalidDataException {
		try {
			userSvc.createUsersInAD();
			return ResponseEntity.ok(new ResponseBean<Boolean>(ROLE_FOUND, true));
		} catch (RecordNotDataException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return ResponseEntity.ok(new ResponseBean<Boolean>(ROLE_FOUND, false));
		}
	}
	
	@PostMapping("/create/ad/user")
	public ResponseEntity<ResponseBean<Boolean>> createUserInAD(@RequestBody User user) throws DataNotFoundException, InvalidDataException {
		
			userSvc.createUserInAD(user);
			return ResponseEntity.ok(new ResponseBean<Boolean>(RECORD_SAVED, true));
	}
		
	@PostMapping("/create/ad/group")
	public ResponseEntity<ResponseBean<Boolean>> createADGroup(@RequestBody GroupDTO group) throws InvalidDataException, LDAPException {
		return ResponseEntity.ok(new ResponseBean<Boolean>(AD_ACTION_SUCCESS, userSvc.createGroup(group)));
	}
		
	@PostMapping("/attach/usertoad/group")
	public ResponseEntity<ResponseBean<Boolean>> attachUserToGroup(@RequestBody GroupDTO group) throws InvalidDataException, LDAPException {
		return ResponseEntity.ok(new ResponseBean<Boolean>(AD_ACTION_SUCCESS, userSvc.attachUserToGroup(group)));
	}

	@PostMapping("/attach/allusertoad/group")
	public ResponseEntity<ResponseBean<Boolean>> attachGroupToAllUsers() throws InvalidDataException, LDAPException {
		return ResponseEntity.ok(new ResponseBean<Boolean>(AD_ACTION_SUCCESS, userSvc.attachGroupToAllUsers()));
	}

	@GetMapping("/users/")
	public ResponseEntity<ResponseBean<UserDto>> getUserIdAndName() throws RecordNotDataException{
		return ResponseEntity.ok(new ResponseBean<UserDto>(ALL_USERS_FOUND,userSvc.getUserIdAndName()));
	}
	
	
	@GetMapping("/users/details/{partnerId}/{customerId}")
	public ResponseEntity<ResponseBean<Map<String, List<EmailDetailsDTO>>>> getUserDetailsAndAggrigatedEmail(@PathVariable("partnerId") String partnerId, @PathVariable("customerId") String customerId) throws RecordNotDataException{
		return ResponseEntity.ok(new ResponseBean<Map<String, List<EmailDetailsDTO>>>(ALL_USERS_FOUND,userSvc.getUserDetailsAndAggrigatedEmail(partnerId,customerId)));
	}
	
	
	
}
