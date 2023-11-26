package com.sv.user.service;

import static com.sv.constant.Message.ALL_USERS_FOUND;
import static com.sv.constant.Message.USER_CLOSE;
import static com.sv.constant.Message.All_USERS_NOT_FOUND;
import static com.sv.constant.Message.DUPLICATE_RECORD;
import static com.sv.constant.Message.INVALID_DATA;
import static com.sv.constant.Message.NO_CHANGES;
import static com.sv.constant.Message.PRIMARY_CONTACT_USER;
import static com.sv.constant.Message.RECORD_NOT_FOUND;
import static com.sv.user.constant.Events.USER_CREATED;
import static com.sv.user.constant.Events.USER_DELETED;
import static com.sv.user.constant.Events.USER_UPDATED;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.kafka.common.config.types.Password;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.sv.config.AppConfig;
import com.sv.user.dto.PasswordExpiredDTO;
import com.sv.dto.AuthRequest;
import com.sv.dto.AuthResponse;
import com.sv.dto.EmailAggregateDTO;
import com.sv.dto.ResponseBean;
import com.sv.dto.TicketDetailsDTO;
import com.sv.dto.TokenInfo;
import com.sv.dto.EmailDetailsDTO;
import com.sv.dto.UserDto;
import com.sv.exception.AuthenticationException;
import com.sv.exception.CurrentAndNewPasswordEqualException;
import com.sv.exception.DataNotFoundException;
import com.sv.exception.DuplicateRecordException;
import com.sv.exception.EventPublisherExecption;
import com.sv.exception.IntegrationException;
import com.sv.exception.InvalidCurrentPasswordException;
import com.sv.exception.InvalidDataException;
import com.sv.exception.LDAPException;
import com.sv.exception.PasswordExpiredException;
import com.sv.exception.PersistenceException;
import com.sv.exception.RecordNotDataException;
import com.sv.exception.UserUsedInOpenTicketException;
import com.sv.security.JWTService;
import com.sv.user.dto.ChangePasswordDTO;
import com.sv.user.dto.GroupDTO;
import com.sv.user.dto.LoginInfoDTO;
import com.sv.user.dto.PasswordExpiredDTO;
import com.sv.user.entity.RoleDetails;
import com.sv.user.entity.SecretQuestions;
import com.sv.user.entity.User;
import com.sv.user.entity.UserAuth;
import com.sv.user.entity.UserHistory;
import com.sv.user.entity.UserPasswordHistory;
import com.sv.user.entity.UserQuestionAnswers;
import com.sv.user.entity.UserTenantMapping;
import com.sv.user.repo.UserAuthRepo;
import com.sv.user.repo.UserHistoryRepo;
import com.sv.user.repo.UserPasswordHistoryRepo;
import com.sv.user.repo.UserQuestionAnswersRepo;
import com.sv.user.repo.UserRepo;
import com.sv.user.repo.UserTenantMappingRepo;
import com.sv.user.util.HashingAlgorithms;
import com.sv.user.util.PasswordUtil;

import io.jsonwebtoken.Claims;

@Service
@Scope("request")
public class UserService implements IUserService {

	private Logger log = LoggerFactory.getLogger(UserService.class);

	@Autowired
	private UserRepo userRepo;

	@Autowired
	private UserAuthRepo authRepo;

	@Autowired
	private RoleService roleSvc;

	@Autowired
	private IUserHistoryService userHistorySvc;

	@Autowired
	private UserHistoryRepo userHistoryRepo;
	
	@Autowired
	private UserPasswordHistoryRepo userPasswordHistoryRepo;

	@Autowired
	private TokenInfo tokenInfo;

	@Autowired
	private AuthService authService;

	@Autowired
	private UserTenantMappingRepo userTenantMappingRepo;

	@Autowired
	private IEventPublisher eventPublisher;

	@Autowired
	private HTTPService httpSvc;

	@Autowired
	private AppConfig appConfig;

	@Autowired
	private UserQuestionAnswersRepo userQuesRepo;

	@Autowired
	private IADService adSvc;
	
	@Autowired
	private JWTService jwtSvc;
	
	@Autowired
	private EntityService entitySvc;
	
	@Override
	public LoginInfoDTO userLoginDetails() throws InvalidDataException, DataNotFoundException {
		String userId = tokenInfo.getUserId();

		List<RoleDetails> roleDetails = roleSvc.findByUserId();

		User user = this.findByUserId(userId);

		if (user == null || roleDetails == null || roleDetails.isEmpty()) {
			throw new DataNotFoundException(RECORD_NOT_FOUND + " For login user, Please contact admin!");
		}

		UserAuth userAuth = authRepo.findByUserId(user.getId());

		UserQuestionAnswers userQuestionAnswers = userQuesRepo.findByUserIdAndActive(user.getId(), true);

		if (userQuestionAnswers != null) {
			userAuth.setSecretQuestion(userQuestionAnswers.isSecreQuestionPresent());
		}

//		LoginInfoDTO dto = new LoginInfoDTO(user.getId(), user.name(), user.getEntityId(), user.getEntityType(),
//				user.getTimeZoneId(), userAuth.isChangePassword(), userAuth.isSecretQuestion());
		
		LoginInfoDTO dto = new LoginInfoDTO(user.getId(), user.name(), user.getEntityId(), user.getEntityType(),
				user.getTimeZoneId(), false, true);

		if (user.getEntityType().equalsIgnoreCase("2")) {
			LinkedHashMap custData = this.fetchCustomerDetailsPartnerInfo(user.getEntityId());

			String partnerId = custData.get("partnerId").toString();
			dto.setPartnerId(partnerId);

			String whiteLabel = custData.get("whiteLabel").toString();
			boolean wLabel = Boolean.parseBoolean(whiteLabel);
			if (wLabel) {
				dto.setLogo((String) custData.get("logo"));
			} else {
				LinkedHashMap partData = this.fetchPartnerDetails(partnerId);
				dto.setLogo((String) partData.get("logo"));
			}

		} else if (!user.getId().equals("d5c0a0c2-3ac0-4f42-bf4d-e554281a738a")) {
			LinkedHashMap partData = this.fetchPartnerDetails(user.getEntityId());
			dto.setLogo((String) partData.get("logo"));

		}

		for (RoleDetails roleDetails2 : roleDetails) {
			dto.addMenus(roleDetails2.getMenuDetails());
		}

		return dto;
	}

	private LinkedHashMap fetchPartnerDetails(String partnerId) {
		try {
			ResponseBean<LinkedHashMap> partner = httpSvc.get(appConfig.getPartnerUrl() + partnerId,
					tokenInfo.getTokenId());
			log.debug("Partner Details Reterived = {}", partner.getData());
			if (partner.getData() != null) {
				return partner.getData();
			}

		} catch (IntegrationException e) {
			e.printStackTrace();
		}
		return null;
	}

	private LinkedHashMap fetchCustomerDetailsPartnerInfo(String customerId) {
		try {
			ResponseBean<LinkedHashMap> customer = httpSvc.get(appConfig.getCustomerUrl() + "/" + customerId,
					tokenInfo.getTokenId());
			log.debug("Customer Details Reterived = {}", customer.getData());
			if (customer.getData() != null && customer.getData().containsKey("partnerId")) {
				return customer.getData();
			}

		} catch (IntegrationException e) {
			e.printStackTrace();
		}
		return null;
	}

	private String fetchCustomerPartnerInfo(String customerId) {
		try {
			ResponseBean<LinkedHashMap> customer = httpSvc.get(appConfig.getCustomerUrl() + "/" + customerId,
					tokenInfo.getTokenId());
			log.debug("Customer Details Reterived = {}", customer.getData());
			if (customer.getData() != null && customer.getData().containsKey("partnerId")) {
				return customer.getData().get("partnerId").toString();
			}

		} catch (IntegrationException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public User createUser(String type, String entityId, User user)
			throws PersistenceException, InvalidDataException, DuplicateRecordException {

		if (user == null || !user.isValid()) {
			throw new InvalidDataException(INVALID_DATA);
		}

		boolean isDuplicate = this.isDuplicateLoginId(user.getLoginId(), null);

		if (isDuplicate) {
			throw new DuplicateRecordException(DUPLICATE_RECORD.trim() + ", please enter different loginId!");
		}

		user.attachAutidDetails(tokenInfo.getUserId(), type, entityId);
		
//		try {
//			boolean flag = adSvc.createUser(user, PasswordUtil.getPassword());
//			if(!flag) {
//				throw new InvalidDataException("Unable to create user in AD, Please contact admin!");
//			}
//		} catch (AuthenticationException e) {
//			e.printStackTrace();
//			log.error("AuthenticationException=",e.getMessage());
//			throw new InvalidDataException("Unable to create user in AD");
//		}
		
		user = userRepo.save(user);

		log.info("User created, id={}", user);

		this.adduserHistory(user, USER_CREATED);

		if (Boolean.parseBoolean(appConfig.getSendMailOnUserCreation())) {
			String fromEmailId=this.getEmailId(user);
			
			this.notifyUserCreationViaEmail(user,fromEmailId);
		}

		if (user.isPrimaryContact()) {
			this.deActivatePrePrimaryContact(entityId, user);
		}

		return user;
	}

	public void notifyUserCreationViaEmail(User user, String fromEmailId) throws PersistenceException, InvalidDataException {
		String pwd = authService.createPassword(user.getId(), user.getLoginId(), tokenInfo.getUserId());

		this.publishEvent(user.name(), user.getEmailId(), user.getId(), tokenInfo.getTokenId(), pwd, user,fromEmailId);
	}

	private List<TicketDetailsDTO> fetchOpenTicketInfo(String userId) {
		List<TicketDetailsDTO> ticketList = new ArrayList<TicketDetailsDTO>();
		List<TicketDetailsDTO> ticketListFromAPI = new ArrayList<TicketDetailsDTO>();
		try {
			ResponseBean<LinkedHashMap> response = httpSvc
					.get(appConfig.getOpenTicketUrl().replace("{userId}", userId), tokenInfo.getTokenId());
			log.debug("Open Ticket Details Reterived = {}", response.getData());
			log.info("Open Ticket Details Reterived = {}", response.getData());

			List<LinkedHashMap> list = (List<LinkedHashMap>) response.getData();
			for (LinkedHashMap linkedHashMap : list) {
				ticketList.add(new TicketDetailsDTO(linkedHashMap.get("id").toString(),
						linkedHashMap.get("number").toString(), linkedHashMap.get("type").toString()));
			}

		} catch (IntegrationException e) {
			e.printStackTrace();
		}
		return ticketList;
	}

	@Override
	public User updateUser(String id, User user) throws PersistenceException, InvalidDataException,
			DataNotFoundException, DuplicateRecordException, UserUsedInOpenTicketException {

		if (id == null || user == null || !user.isValid()) {
			throw new InvalidDataException(INVALID_DATA);
		}

		User user2 = userRepo.findByIdAndActive(id, true);

		if (user2 == null) {
			throw new DataNotFoundException(RECORD_NOT_FOUND);
		} else if (user2.uniqueString().equals(user.uniqueString())) {
			throw new InvalidDataException(NO_CHANGES);
		} else if (user2.isPrimaryContact() && !user.isEnabled()) {
			throw new InvalidDataException(PRIMARY_CONTACT_USER);
		}

		boolean isDuplicate = this.isDuplicateLoginId(user.getLoginId(), id);

		if (isDuplicate) {
			throw new DuplicateRecordException(DUPLICATE_RECORD.trim() + ", please enter different loginid!");
		}

		List<TicketDetailsDTO> finalList = this.fetchOpenTicketInfo(id);
		String msg = " ";
		for (TicketDetailsDTO tdetails : finalList) {
			msg = msg + tdetails.getNumber() + ",";
		}

		if (finalList != null && !finalList.isEmpty()) {
			if (!user.isEnabled()) {
				throw new UserUsedInOpenTicketException(USER_CLOSE + msg);
			}
		}

		user2.updateData(user, tokenInfo.getUserId(), user2.getEntityType(), user2.getEntityId());

		user2 = userRepo.save(user2);

		log.info("User Updated, id={}", user2);

		this.adduserHistory(user2, USER_UPDATED);

		if (user.isPrimaryContact()) {
			this.deActivatePrePrimaryContact(user2.getEntityId(), user2);
		}

		try {
			eventPublisher.userUpdated(tokenInfo.getTokenId(), user2);
		} catch (EventPublisherExecption e) {
			log.info("Publishing user updated event failed, Exception=", e.getMessage());
		}

		return user2;
	}
	
	///------------------------------------------------------------------------------------------------------
	
	
	public PasswordExpiredDTO checkPasswordExpiry(String user_id) throws DataNotFoundException {
//		String user_id = tokenInfo.getUserId();
		
		UserPasswordHistory entity = userPasswordHistoryRepo.findFirstByUserIdOrderByCreateDateDesc(user_id);
		
		if (entity!=null) {
			 Date old  = entity.getCreateDate();
			 LocalDate oldLocalDate =old.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
			 LocalDate todayLocalDate = LocalDate.now();
			 long days = ChronoUnit.DAYS.between(oldLocalDate, todayLocalDate);
			 
			 //  Checking days
			 if(days>=30) {
				 return new PasswordExpiredDTO(true, "Password Expeiry Status Found !", true);
			 }
			 else {
				 return new PasswordExpiredDTO(true, "Password Expeiry Status Found !", false);
			 }
		}
		else {
			throw new DataNotFoundException("User password histroy details not found!");
		}
	}

	private void deActivatePrePrimaryContact(String entityId, User _user) {
		List<User> users = userRepo.findByEntityId(entityId, Sort.by("firstName", "lastName"));
		for (User user : users) {
			if (!user.getId().equals(_user.getId())) {
				user.setPrimaryContact(false);
				userRepo.save(user);
			}
		}

		try {
			if (_user.getEntityType().equals("2")) {
				this.httpSvc.put(appConfig.getCustUpdateUrl().replace("{id}", entityId), tokenInfo.getTokenId(),
						_user.primaryContactInfoRequest());
			} else {
				this.httpSvc.put(appConfig.getPtrUpdateUrl().replace("{id}", entityId), tokenInfo.getTokenId(),
						_user.primaryContactInfoRequest());
			}
		} catch (IntegrationException e) {
			e.printStackTrace();
			log.error("API call failed to map primary contact info, IntegrationException={}", e.getMessage());
		}

	}

	@Override
	public User updateUserTimezone(String id, User userTz)
			throws PersistenceException, InvalidDataException, DataNotFoundException, DuplicateRecordException {

		if (id == null || userTz == null) {
			throw new InvalidDataException(INVALID_DATA);
		}

		User user2 = userRepo.findByIdAndActive(id, true);

		if (user2 == null) {
			throw new DataNotFoundException(RECORD_NOT_FOUND);
		} else {

			user2.updateTimeZone(userTz.getTimeZoneId(), tokenInfo.getUserId());

			user2 = userRepo.save(user2);

			log.info("User Timezone Updated, id={}", user2);

			this.adduserHistory(user2, USER_UPDATED);

			try {
				eventPublisher.userUpdated(tokenInfo.getTokenId(), user2);
			} catch (EventPublisherExecption e) {
				log.info("Publishing user updated event failed, Exception=", e.getMessage());
			}

			return user2;
		}
	}

	@Override
	public String deleteUser(String id) throws PersistenceException, InvalidDataException, DataNotFoundException {

		if (id == null || id.isEmpty()) {
			throw new InvalidDataException(INVALID_DATA);
		}

		User user = userRepo.findByIdAndActive(id, true);

		if (user == null) {
			throw new DataNotFoundException(RECORD_NOT_FOUND);
		}

		user.deactivate(tokenInfo.getUserId());

		user = userRepo.save(user);
		log.info("User Deleted, id={}", user);

		this.adduserHistory(user, USER_DELETED);

		return id;
	}

	private void adduserHistory(User user, String event) throws PersistenceException {
		try {
			roleSvc.attachRole(user.getId(), user.getRoleId());
		} catch (InvalidDataException e) {
			e.printStackTrace();
		}
		userHistorySvc.appendHistory(user, event);
	}

	private boolean isDuplicateLoginId(String name, String id) {

		List<User> users = userRepo.findByLoginId(name);

		if ((id == null || id.isEmpty()) && users.size() > 0) {
			return true;
		} else if ((id != null && !id.isEmpty()) && users.size() > 0) {
			for (User user : users) {
				if (!user.getId().equalsIgnoreCase(id) && user.getLoginId().equalsIgnoreCase(name)) {
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public List<User> getAllUsers(String type, String referenceId) throws DataNotFoundException {
		List<User> data = userRepo.findByEntityTypeAndEntityId(type, referenceId);

		if (data != null && !data.isEmpty()) {
			log.debug("{} No of User = {}", ALL_USERS_FOUND, data.size());
			return data;
		}

		log.warn(All_USERS_NOT_FOUND);
		throw new DataNotFoundException(All_USERS_NOT_FOUND);
	}

	@Override
	public List<User> getAllEnabledUsers(String type, String referenceId) throws DataNotFoundException {
		// List<User> data = userRepo.findByEntityTypeAndEntityIdAndEnabled(type,
		// referenceId, true);
		List<User> data = userRepo.findByEntityIdAndEnabled(referenceId, true, Sort.by("firstName", "lastName"));
		if (data != null && !data.isEmpty()) {
			log.debug("{} No of User = {}", ALL_USERS_FOUND, data.size());
			return data;
		}

		log.warn(All_USERS_NOT_FOUND);
		throw new DataNotFoundException(All_USERS_NOT_FOUND);
	}

	@Override
	public List<User> getAllUser(String referenceId) throws DataNotFoundException {

		List<User> allUsers = new ArrayList<User>();
		List<User> removedDuplicateUsers = new ArrayList<User>();
		List<String> userId = new ArrayList<String>();

		List<User> custUser = userRepo.findByEntityIdAndEnabled(referenceId, true, Sort.by("firstName", "lastName"));

		if (custUser != null && !custUser.isEmpty()) {
			log.debug("{} No of User = {}", ALL_USERS_FOUND, custUser.size());
			allUsers.addAll(custUser);
		}

		String partnerId = this.fetchCustomerPartnerInfo(referenceId);

		List<User> partnerUser = userRepo.findByEntityIdAndEnabled(partnerId, true, Sort.by("firstName", "lastName"));

		if (partnerUser != null && !partnerUser.isEmpty()) {
			log.debug("{} No of User = {}", ALL_USERS_FOUND, partnerUser.size());
			allUsers.addAll(partnerUser);
		}

		List<User> mspUser = userRepo.findByEntityTypeAndEnabled("0", true,
				Sort.by("firstName", "lastName", "entityType"));

		if (mspUser != null && !mspUser.isEmpty()) {
			log.debug("{} No of User = {}", ALL_USERS_FOUND, partnerUser.size());
			allUsers.addAll(mspUser);
		}

		if (allUsers != null && !allUsers.isEmpty()) {

			for (User us : allUsers) {
				if (!userId.contains(us.getId())) {
					userId.add(us.getId());
					removedDuplicateUsers.add(us);
				}
			}

			Collections.sort(removedDuplicateUsers);

			return removedDuplicateUsers;
		} else {
			log.warn(All_USERS_NOT_FOUND);
			throw new DataNotFoundException(All_USERS_NOT_FOUND);
		}

	}

	@Override
	public List<User> getMspAndPartnerUser(String referenceId) throws DataNotFoundException {

		List<User> mspAndPartnerUsers = new ArrayList<User>();
		List<User> removedDuplicateUsers = new ArrayList<User>();
		List<String> userId = new ArrayList<String>();

		String partnerId = this.fetchCustomerPartnerInfo(referenceId);

		List<User> partnerUser = userRepo.findByEntityIdAndEnabled(partnerId, true, Sort.by("firstName", "lastName"));

		if (partnerUser != null && !partnerUser.isEmpty()) {
			log.debug("{} No of User = {}", ALL_USERS_FOUND, partnerUser.size());
			mspAndPartnerUsers.addAll(partnerUser);
		}

		List<User> mspUser = userRepo.findByEntityTypeAndEnabled("0", true,
				Sort.by("firstName", "lastName", "entityType"));

		if (mspUser != null && !mspUser.isEmpty()) {
			log.debug("{} No of User = {}", ALL_USERS_FOUND, partnerUser.size());
			mspAndPartnerUsers.addAll(mspUser);
		}

		if (mspAndPartnerUsers != null && !mspAndPartnerUsers.isEmpty()) {

			for (User us : mspAndPartnerUsers) {
				if (!userId.contains(us.getId())) {
					userId.add(us.getId());
					removedDuplicateUsers.add(us);
				}
			}

			Collections.sort(removedDuplicateUsers);
			return removedDuplicateUsers;
		} else {
			log.warn(All_USERS_NOT_FOUND);
			throw new DataNotFoundException(All_USERS_NOT_FOUND);
		}

	}

	@Override
	public List<User> getAllUsers(String type) throws DataNotFoundException {
		List<User> data = userRepo.findByEntityType(type, Sort.by("firstName", "lastName", "entityType"));

		if (data != null && !data.isEmpty()) {
			log.debug("{} No of User = {}", ALL_USERS_FOUND, data.size());
			return data;
		}

		log.warn(All_USERS_NOT_FOUND);
		throw new DataNotFoundException(All_USERS_NOT_FOUND);
	}

	@Override
	public List<User> getAllUsersByEntityId(String entityId) throws DataNotFoundException {
		List<User> data = userRepo.findByEntityIdAndEnabled(entityId, true, Sort.by("firstName", "lastName"));

		if (data != null && !data.isEmpty()) {
			log.debug("{} No of User = {}", ALL_USERS_FOUND, data.size());
			return data;
		}

		log.warn(All_USERS_NOT_FOUND);
		throw new DataNotFoundException(All_USERS_NOT_FOUND);
	}

	@Override
	public User findByUserId(String id) throws DataNotFoundException, InvalidDataException {
		if (id == null) {
			throw new InvalidDataException(INVALID_DATA);
		}

		User user = userRepo.findByIdAndActive(id, true);
		if (user == null) {
			log.warn(RECORD_NOT_FOUND);
			throw new DataNotFoundException(RECORD_NOT_FOUND);
		}

		log.debug("User details reterived = {}", user);

		return user;
	}

	@Override
	public LoginInfoDTO fetchUserSessionContext(String id) throws DataNotFoundException, InvalidDataException {
		if (id == null) {
			throw new InvalidDataException(INVALID_DATA);
		}

		User user = userRepo.findByIdAndActive(id, true);
		if (user == null) {
			log.warn(RECORD_NOT_FOUND);
			throw new DataNotFoundException(RECORD_NOT_FOUND);
		}

		if (user.getEntityType().equalsIgnoreCase("2")) {

			return new LoginInfoDTO(user.getId(), user.name(), user.getEntityId(), user.getEntityType(), null, null);
		} else {
			UserTenantMapping userTenantMapping = userTenantMappingRepo.findByUserIdAndActive(id, true);

//			if(userTenantMapping != null) {
//				return new LoginInfoDTO(user.getId(), user.name(), user.getEntityId(), user.getEntityType(), userTenantMapping.getTenantInfo());
//			}else {
//			 
//				return new LoginInfoDTO(user.getId(), user.name(), user.getEntityId(), user.getEntityType(), null);
//			}

			return new LoginInfoDTO(user.getId(), user.name(), user.getEntityId(), "0", null, null);

		}

	}

	private void publishEvent(String name, String emailId, String id, String token, String password, User user, String fromEmailId) {
		try {
//			eventPublisher.userCreated(token, user);
			eventPublisher.userWelcome(user.getLoginId(), name, emailId, id, token,fromEmailId);
			eventPublisher.userPassword("USER_PASSWORD", name, emailId, password, id, token,fromEmailId);
		} catch (EventPublisherExecption e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public boolean resetPassword(String id)
			throws DataNotFoundException, InvalidDataException, PersistenceException, EventPublisherExecption {
		if (id == null) {
			throw new InvalidDataException(INVALID_DATA);
		}

		User user = userRepo.findByIdAndActive(id, true);
		if (user == null) {
			log.warn(RECORD_NOT_FOUND);
			throw new DataNotFoundException(RECORD_NOT_FOUND);
		}

		UserAuth userAuth = authRepo.findByUserId(user.getId());

		if(userAuth == null) {
			log.warn("Could not reset password, please click on welcome email!");
			throw new DataNotFoundException("Could not reset password, please click on welcome email!");
		}
		log.debug("Reseting password od = {}", user);

		// Need to refactor
		log.debug("TokenInfo={}", tokenInfo);

		if (tokenInfo != null && tokenInfo.getTokenId() == null) {
			try {
				log.info("Generate Token");
				String tokenId = this.createToken(user);
				Claims claim = jwtSvc.getAllClaimsFromToken(tokenId);

				tokenInfo.updateTokenInfo(tokenId, claim.getSubject());

			} catch (Exception e) {

				e.printStackTrace();
			}

		}
	
		String pwd = authService.resetPassword(user.getId(), user.getLoginId(),  PasswordUtil.getPassword());

				
		String fromEmailId = getEmailId(user);
		
		eventPublisher.userPassword("USER_PASSWORD", user.name(), user.getEmailId(), pwd, id, tokenInfo.getTokenId(),fromEmailId);

		return true;
	}
	
	public String createToken(User user) throws AuthenticationException {
		try {
			TokenInfo tokenInfo = new TokenInfo(UUID.randomUUID().toString(), user.getId(), user.getEntityType(), user.name(), user.getTimeZoneId());
			return jwtSvc.generateToken(tokenInfo);
		} catch (Exception e) {
			log.error("Error while creating token, exception={}", e.getMessage());
			log.error("Exception=", e);
			throw new AuthenticationException("Unable to create token");
		}
	}

	private String getEmailId(User user) {
//		String fromEmailId = "support@securview.com";
		String fromEmailId =appConfig.getFromEmailId();
		if(user.getEntityType() !=null && user.getEntityType().equals("2")) {
			try {
				ResponseBean<LinkedHashMap<String, Object>> response = this.httpSvc.get( appConfig.getCustomerDetailAPI().replace("{custId}", user.getEntityId()), tokenInfo.getTokenId());
				log.debug("Customer Info={}", response.toJSON());

				if (response != null && response.isSuccess()) {
					LinkedHashMap<String, Object> cusomer = response.getData();
					String whiteLabelStr = cusomer.get("whiteLabel").toString();
					boolean whiteLabel = Boolean.parseBoolean(whiteLabelStr);
					
					if(whiteLabel && cusomer.get("emailPrefix")!=null &&  cusomer.get("emailPrefix").toString().trim().length()>0) {
						String c_url = cusomer.get("url").toString();
						String[] c_urls = c_url.split("\\.", 2);
						fromEmailId = cusomer.get("emailPrefix") + "@" + c_urls[1];
						log.info("Customer FromEmailId :" + fromEmailId);
					}
					
					
					if (whiteLabel) {
						String partnerId = cusomer.get("partnerId").toString();
						ResponseBean<LinkedHashMap<String, String>> partnerResponse = this.httpSvc.get(appConfig.getPartnerDetailsAPI().replace("{partnerId}", partnerId), tokenInfo.getTokenId());
						log.debug("Partner Info={}", response.toJSON());
						if (partnerResponse != null && partnerResponse.isSuccess()) {
							LinkedHashMap<String, String> partner = partnerResponse.getData();
							if(partner.get("emailPrefix")!=null && partner.get("emailPrefix").toString().trim().length()>0) {
								String p_url = partner.get("url").toString();
								String[] p_urls = p_url.split("\\.", 2);
								fromEmailId = partner.get("emailPrefix") + "@" + p_urls[1];
								log.info("Partner FromEmailId :" + fromEmailId);

							}
							
						}
					}

				}

			} catch (IntegrationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		log.info("fromEmailId :" + fromEmailId);
		return fromEmailId;
	}
	
	
	private void addHistory(String userId, String eventType, String change) {

		UserHistory userHistory = UserHistory.getInstance(userId, eventType, userId, tokenInfo.getUserName(), change);
		userHistory = userHistoryRepo.save(userHistory);
		log.debug("User History added userid={}, id={}", userId, userHistory.getId());
	}

	@Override
	public boolean sendWelcomeMail(String id)
			throws DataNotFoundException, InvalidDataException, PersistenceException, EventPublisherExecption {
		if (id == null) {
			throw new InvalidDataException(INVALID_DATA);
		}

		User user = userRepo.findByIdAndActive(id, true);
		if (user == null) {
			log.warn(RECORD_NOT_FOUND);
			throw new DataNotFoundException(RECORD_NOT_FOUND);
		}
		
// 		throw exception if allready email send
		UserAuth userAuth = authRepo.findByUserId(id);
		log.debug("sending welcome message to user = {}", user);

		if (userAuth != null) {
//			throw new InvalidDataException("Welcome Email allready send to you!");
			this.resetPassword(id);
		} else {			
			String fromEmailId = getEmailId(user);
			this.notifyUserCreationViaEmail(user, fromEmailId);
		}



		this.addHistory(user.getId(), "USER_WELCOME", "Welcome email sent by "+tokenInfo.getUserName());

		return true;
	}

	@Override
	public boolean changePassword(ChangePasswordDTO dto)
			throws DataNotFoundException, InvalidDataException, PersistenceException, EventPublisherExecption,
			InvalidCurrentPasswordException, CurrentAndNewPasswordEqualException {

		if (dto == null || !dto.isValid()) {
			log.warn("Please enter valid data to change the password!");
			throw new InvalidDataException("Please enter valid data to change the password!");
		} else {
			User user = userRepo.findByIdAndActive(tokenInfo.getUserId(), true);
			if (user == null) {
				log.warn("User " + RECORD_NOT_FOUND + " to change password");
				throw new DataNotFoundException("User " + RECORD_NOT_FOUND + " to change password");
			}
			if (tokenInfo.getUserId() != null) {
				String password = dto.getCurrentPassword();
				String confirmpassword = dto.getConfirmPassword();
				
				boolean adAuthStatus = false;
				try {
					AuthResponse authResp =  authService.authenticate(new AuthRequest(user.getLoginId(), password));
					if(authResp!=null) {
						adAuthStatus = true;
					}
				} catch (AuthenticationException e) {
					log.info("Authentication failed, AuthenticationException={}",e.getMessage());
				}
				
				
				if(!adAuthStatus) {
					log.warn("Current Password does not match");
					throw new InvalidCurrentPasswordException("Current Password does not match");
				}
				
				if(confirmpassword.contains(user.getFirstName()) || confirmpassword.contains(user.getLastName())) {
					log.warn("Current password does not match security context, Make sure user first, last and middle name is not present in new password");
					throw new InvalidCurrentPasswordException("Current password does not match security context, Make sure user first, last and middle name is not present in new password");
				}
				
				if (password.equals(confirmpassword)) {
					log.warn("Current and new Password are equal");
					throw new CurrentAndNewPasswordEqualException("Current and new Password are equal");
				} else {
					String pwd = authService.changePassword(tokenInfo.getUserId(), user.getLoginId(),dto.getConfirmPassword());
					log.info("Password changed of userid={}", tokenInfo.getUserId());
					
					String fromEmailId=this.getEmailId(user);
					
					eventPublisher.userPassword("USER_PASSWORD_CHANGED", user.name(), user.getEmailId(), pwd,
							tokenInfo.getUserId(), tokenInfo.getTokenId(),fromEmailId);
					
					this.addHistory(user.getId(), "USER PASSWORD CHANGED", "Password has been changed by "+tokenInfo.getUserName());
					
					return true;
				}
				
//				UserAuth userAuth = authRepo.findByUserId(tokenInfo.getUserId());
//				try {
//					password = HashingAlgorithms.getInstance().createHash(dto.getCurrentPassword());
//					confirmpassword = HashingAlgorithms.getInstance().createHash(dto.getConfirmPassword());
//				} catch (Exception e) {
//					e.printStackTrace();
//					log.error("Error while validating password, exception={}", e.getMessage());
//					log.error("Exception=", e);
//				}
				/*
				if (userAuth.getPassword().equals(password)) {
					if (password.equals(confirmpassword)) {
						throw new CurrentAndNewPasswordEqualException("Current and new Password are equal");
					} else {
						String pwd = authService.changePassword(tokenInfo.getUserId(), user.getLoginId(),dto.getConfirmPassword());
						log.info("Password changed of userid={}", tokenInfo.getUserId());
						eventPublisher.userPassword("USER_PASSWORD_CHANGED", user.name(), user.getEmailId(), pwd,
								tokenInfo.getUserId(), tokenInfo.getTokenId());
						
						this.addHistory(user.getId(), "USER PASSWORD CHANGED", "Password has been changed by "+tokenInfo.getUserName());
						
						return true;
					}
				} else {
					throw new InvalidCurrentPasswordException("Current Password does not match");
				}*/

				
				
			} else {
				log.warn("Cannot change password, please get password reset from Admin!");
				throw new InvalidDataException("Cannot change password, please get password reset from Admin!");
			}
			
			

		}
	}

	@Override
	public UserQuestionAnswers saveUserQuestionsAnswers(UserQuestionAnswers userQuestionAnswers)
			throws InvalidDataException, DuplicateRecordException {
		userQuestionAnswers.setUserId(tokenInfo.getUserId());

		if (userQuestionAnswers.hasDuplicateQuestion()) {
			throw new DuplicateRecordException("Duplicate Question");
		}

		UserQuestionAnswers prevRecord = userQuesRepo.findByUserIdAndActive(tokenInfo.getUserId(), true);

		if (prevRecord != null) {
			prevRecord.deactivate(tokenInfo.getUserId());
			userQuesRepo.save(prevRecord);
		}

		userQuestionAnswers.attachAutidDetails(tokenInfo.getUserId());
		return userQuesRepo.save(userQuestionAnswers);
	}

	@Override
	public UserQuestionAnswers findUserByUserId(String loginId, boolean resetAnswer) throws DataNotFoundException {
		UserQuestionAnswers userQuestionAnswers = new UserQuestionAnswers();
		User user = userRepo.findByLoginIdAndActive(loginId, true);
		if (user == null) {
			throw new DataNotFoundException("User not found!");
		} else {
			userQuestionAnswers = userQuesRepo.findByUserIdAndActive(user.getId(), true);
			if (userQuestionAnswers == null) {
				throw new DataNotFoundException(RECORD_NOT_FOUND);
			}
		}
		
		if(resetAnswer) {
			userQuestionAnswers.resetAnswer();
		}
		
		return userQuestionAnswers;
	}

	@Override
	public UserQuestionAnswers findUserQuestionByLoginId() throws DataNotFoundException {
		UserQuestionAnswers userQuestionAnswers =  userQuesRepo.findByUserIdAndActive(tokenInfo.getUserId(), true);
		if (userQuestionAnswers == null) {
			throw new DataNotFoundException(RECORD_NOT_FOUND);
		}
		
		return userQuestionAnswers;
	}

	
	@Override
	public boolean checkUserQuestionsAnswers(UserQuestionAnswers userQuestionAnswers)
			throws DataNotFoundException, InvalidDataException, PersistenceException, EventPublisherExecption {
		boolean verifiedUser = false;
		User user = userRepo.findByLoginIdAndActive(userQuestionAnswers.getUserId(), true);

		if (user != null) {
			UserQuestionAnswers userQuestionAnswersDB = userQuesRepo.findByUserIdAndActive(user.getId(), true);
			if (userQuestionAnswersDB != null) {
				for (SecretQuestions secretQuestions1 : userQuestionAnswersDB.getSecretQuestions()) {
					for (SecretQuestions secretQuestions2 : userQuestionAnswers.getSecretQuestions()) {
						if (secretQuestions1.getQuestionLabel().equals(secretQuestions2.getQuestionLabel())) {
							if (secretQuestions1.getAnswer().equals(secretQuestions2.getAnswer())) {
								verifiedUser = true;
							} else {
								verifiedUser = false;
								return false;
							}

						}
					}
				}
			} else {
				log.info("User details not found, id={}", user.getId());
			}

			if (verifiedUser) {
				this.resetPassword(user.getId());
			}
		}
		log.info("User details not found, loginid={}", userQuestionAnswers.getUserId());
		return verifiedUser;
	}

	@Override
	public UserQuestionAnswers getUserQuestions() throws RecordNotDataException {
		UserQuestionAnswers record = userQuesRepo.findByUserIdAndActive(tokenInfo.getUserId(), true);
		if (record == null) {
			throw new RecordNotDataException("User secret questions are not defined");
		}
		return record;
	}
	
	@Override
	public void createUsersInAD() throws RecordNotDataException {
		List<User> users = userRepo.findAll();
		int count = 1;
		int size = users.size();
		for (User user : users) {
			try {
				if(user.getLoginId().equalsIgnoreCase("sanjay") || true ) {
					String password = "Ng@Prod2023";
					boolean status = adSvc.createUser(user, password);
					log.info("AD User Creation status={} for loginid={}, count={}/{} ",status, user.getLoginId(),count,size);
					count = count + 1;
				}
				
			}catch (Exception e) {
				log.error("AD User Creation failed={} for loginid={} ",e.getMessage(),user.getLoginId());
			}
			
		}
	
	}

	@Override
	public void createUserInAD(User user)  {

		try {
			String password = PasswordUtil.getPassword();
			boolean status = adSvc.createUser(user, password);
			log.info("AD User Creation status={} for loginid={}, count={}/{} ",status, user.getLoginId());
		} catch (AuthenticationException e) {
			e.printStackTrace();
		}
	
	}
	
	@Override
	public boolean createGroup(GroupDTO group) throws LDAPException, InvalidDataException {
		return adSvc.createGroup(group);
	}
	
	
	@Override
	public boolean attachUserToGroup(GroupDTO group) throws LDAPException, InvalidDataException {
		return adSvc.attachUserToGroup(group);
	}
	
	@Override
	public boolean attachGroupToAllUsers() throws InvalidDataException, LDAPException {
		List<User> users = userRepo.findAll();
		int count = 1;
		int size = users.size();
		for (User user : users) {
			try {
				String groupName = entitySvc.getGroupName(user.getEntityId(), user.getEntityType()); 
				boolean status = this.attachUserToGroup(new GroupDTO(groupName, user.getLoginId()));
				log.info("User={} attached to AD group={}. Count={}/{}, Success={} ",user.getLoginId(), groupName, count,size, status);
				count = count + 1;
				
			}catch (Exception e) {
				log.error("Attaching User={} To group failed={} ",user.getLoginId(),e.getMessage());
			}
			
		}
		return true;
	}
	

	
	@Override
	public UserDto getUserIdAndName() throws RecordNotDataException {
		UserDto userDto = new UserDto();
		List<User> users = userRepo.findAll();
		if(users!=null) {
			
			for(User user : users) {
				userDto.setUser(user.getId(), user.getFirstName()+" "+user.getLastName());
			}
			
		}else {
			throw new RecordNotDataException(RECORD_NOT_FOUND);
		}
		return userDto;
	}
	
	
	public Map<String, List<EmailDetailsDTO>> getUserDetailsAndAggrigatedEmail(String partnerId, String customerId) throws RecordNotDataException{
		
		List<User> allUsers = new ArrayList<User>();
		
		HashMap<String, List<EmailDetailsDTO>> hm=new LinkedHashMap<String, List<EmailDetailsDTO>>();
		
		List<EmailDetailsDTO> msp=new ArrayList<EmailDetailsDTO>();
		List<EmailDetailsDTO> partner=new ArrayList<EmailDetailsDTO>();
		List<EmailDetailsDTO> customer=new ArrayList<EmailDetailsDTO>();
		List<EmailDetailsDTO> partnerGroup=new ArrayList<EmailDetailsDTO>();
		List<EmailDetailsDTO> customerGroup=new ArrayList<EmailDetailsDTO>();
		
		
		List<User> pUserData = userRepo.findByEntityIdAndEnabled(partnerId, true, Sort.by("firstName", "lastName"));
		
		for(User user : pUserData) {
			User usr=userRepo.findByIdAndActive(user.getId(), true);
			EmailDetailsDTO emailDetails=new EmailDetailsDTO();
			emailDetails.setName(usr.getFirstName() +" "+ usr.getLastName());
			emailDetails.setType(usr.getEntityType());
			emailDetails.setId(usr.getId());
			List<String> emailId=new ArrayList<String>();
			emailId.add(usr.getEmailId());
			emailDetails.setEmailIds(emailId);
			
			partner.add(emailDetails);
		}

		
		List<User> cUserData = userRepo.findByEntityIdAndEnabled(customerId, true, Sort.by("firstName", "lastName"));

		for(User user : cUserData) {
			User usr=userRepo.findByIdAndActive(user.getId(), true);
			EmailDetailsDTO emailDetails=new EmailDetailsDTO();
			emailDetails.setName(usr.getFirstName() +" "+ usr.getLastName());
			emailDetails.setType(usr.getEntityType());
			emailDetails.setId(usr.getId());
			List<String> emailId=new ArrayList<String>();
			emailId.add(usr.getEmailId());
			emailDetails.setEmailIds(emailId);
			
			customer.add(emailDetails);
		}
		
		
		List<User> mspUser = userRepo.findByEntityTypeAndEnabled("0", true,	Sort.by("firstName", "lastName", "entityType"));
		
		for(User user : mspUser) {
			User usr=userRepo.findByIdAndActive(user.getId(), true);
			EmailDetailsDTO emailDetails=new EmailDetailsDTO();
			emailDetails.setName(usr.getFirstName() +" "+ usr.getLastName());
			emailDetails.setType(usr.getEntityType());
			emailDetails.setId(usr.getId());
			List<String> emailId=new ArrayList<String>();
			emailId.add(usr.getEmailId());
			emailDetails.setEmailIds(emailId);
			
			msp.add(emailDetails);
		}
		
			
		
//		get All Partner Group
		try {
			ResponseBean<List<LinkedHashMap<String, Object>>> response = httpSvc.get(appConfig.getpGroupUrl(),
					tokenInfo.getTokenId());
			log.debug("Partner Details Reterived = {}", response.getData());
			
			if (response != null && response.isSuccess()) {
				
				List<LinkedHashMap<String, Object>> partnerDetails = response.getData();
				
				if(!partnerDetails.isEmpty()) {
					for(LinkedHashMap<String, Object> pDetails: partnerDetails) {
						
						List<LinkedHashMap<String, Object>> groupDetails= (List<LinkedHashMap<String, Object>>) pDetails.get("groups");
						
						if(!groupDetails.isEmpty()) {
							
							for(LinkedHashMap<String, Object> gDetails: groupDetails) {
								
								List<LinkedHashMap<String, Object>> userDetails= (List<LinkedHashMap<String, Object>>) gDetails.get("users");
							
//								if(!userDetails.isEmpty()) {
//									for(LinkedHashMap<String, Object> usrDetail: userDetails) {
//										EmailDetailsDTO emailDetails=new EmailDetailsDTO();
//										emailDetails.setName("partner group");
//										emailDetails.setType(usrDetail.get("type").toString());
//										emailDetails.setId(usrDetail.get("userId").toString());
//										List<String> emailId=new ArrayList<String>();
//										emailId.add(usrDetail.get("emailId").toString());
//										emailDetails.setEmailIds(emailId);
//										partnerGroup.add(emailDetails);
//									}
//								}
								
								if(!userDetails.isEmpty()) {
									EmailDetailsDTO emailDetails=new EmailDetailsDTO();
									emailDetails.setName("partner group");
									emailDetails.setType("1");
									emailDetails.setId("-1");
									List<String> emailId=new ArrayList<String>();
									for(LinkedHashMap<String, Object> usrDetail: userDetails) {
										emailId.add(usrDetail.get("emailId").toString());
									}
									
									emailDetails.setEmailIds(emailId);
									partnerGroup.add(emailDetails);
									
								}
								
							}
						}
					}
				}
				
				
				
			}
			
		} catch (IntegrationException e) {
			e.printStackTrace();
		}
		
		
//		get All Customer Group
		
		try {
			ResponseBean<List<LinkedHashMap<String, Object>>> response = httpSvc.get(appConfig.getcGroupUrl(),
					tokenInfo.getTokenId());
			log.debug("Partner Details Reterived = {}", response.getData());
			
			if (response != null && response.isSuccess()) {
				
				List<LinkedHashMap<String, Object>> customerDetails = response.getData();
				
				if(!customerDetails.isEmpty()) {	
					
					for(LinkedHashMap<String, Object> cDetails: customerDetails) {
						
						List<LinkedHashMap<String, Object>> custDetails= (List<LinkedHashMap<String, Object>>) cDetails.get("customers");
						
						if(!custDetails.isEmpty()) {
							
						for(LinkedHashMap<String, Object> custDetail: custDetails) {	
						
						List<LinkedHashMap<String, Object>> groupDetails= (List<LinkedHashMap<String, Object>>) custDetail.get("groups");
						
						if(!groupDetails.isEmpty()) {
							
							for(LinkedHashMap<String, Object> gDetails: groupDetails) {
								
								List<LinkedHashMap<String, Object>> userDetails= (List<LinkedHashMap<String, Object>>) gDetails.get("users");
							
//								if(!userDetails.isEmpty()) {
//									for(LinkedHashMap<String, Object> usrDetail: userDetails) {
//										EmailDetailsDTO emailDetails=new EmailDetailsDTO();
//										emailDetails.setName("customer group");
//										emailDetails.setType(usrDetail.get("type").toString());
//										emailDetails.setId(usrDetail.get("userId").toString());
//										List<String> emailId=new ArrayList<String>();
//										emailId.add(usrDetail.get("emailId").toString());
//										emailDetails.setEmailIds(emailId);
//										
//										customerGroup.add(emailDetails);
//										
//									}
//									
//								}
								
								if(!userDetails.isEmpty()) {
									EmailDetailsDTO emailDetails=new EmailDetailsDTO();
									emailDetails.setName("customer group");
									emailDetails.setType("2");
									emailDetails.setId("-1");
									List<String> emailId=new ArrayList<String>();
									for(LinkedHashMap<String, Object> usrDetail: userDetails) {
										emailId.add(usrDetail.get("emailId").toString());
									}
									emailDetails.setEmailIds(emailId);
									customerGroup.add(emailDetails);
								}
							}
						}
						}
					}
						
					}
					
				}
			}
			
		} catch (IntegrationException e) {
			e.printStackTrace();
		}		
		
		hm.put("msp", msp);
		hm.put("partner", partner);
		hm.put("customer", customer);
		hm.put("partnerGroup", partnerGroup);
		hm.put("customerGroup", customerGroup);
		
		return hm;
	}
}