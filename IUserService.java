package com.sv.user.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.sv.user.entity.User;
import com.sv.user.entity.UserQuestionAnswers;


public interface IUserService {

	public LoginInfoDTO userLoginDetails() throws  InvalidDataException, DataNotFoundException;

	public User createUser(String type, String referenceId, User user) throws PersistenceException, InvalidDataException, DuplicateRecordException;

	public User updateUser(String id, User user) throws PersistenceException, InvalidDataException, DataNotFoundException, DuplicateRecordException, UserUsedInOpenTicketException;
	
	public boolean createGroup(GroupDTO group) throws InvalidDataException, LDAPException;
	
	public boolean attachUserToGroup(GroupDTO group) throws InvalidDataException,LDAPException;
	
	public boolean attachGroupToAllUsers() throws InvalidDataException,LDAPException;
	
	public User updateUserTimezone(String id, User userTz) throws PersistenceException, InvalidDataException, DataNotFoundException, DuplicateRecordException;
	
	public String deleteUser(String id) throws PersistenceException, InvalidDataException, DataNotFoundException;

	public List<User> getAllUsers(String type, String referenceId) throws DataNotFoundException;
	
	public List<User> getAllEnabledUsers(String type, String referenceId) throws DataNotFoundException;
	
	public List<User> getAllUser(String referenceId) throws DataNotFoundException;
	
	public List<User> getMspAndPartnerUser(String referenceId) throws DataNotFoundException;

	public List<User> getAllUsers(String type) throws DataNotFoundException;

	public List<User> getAllUsersByEntityId(String entityId) throws DataNotFoundException;

	public User findByUserId(String id) throws DataNotFoundException, InvalidDataException;

	public boolean resetPassword(String id) throws DataNotFoundException, InvalidDataException, PersistenceException, EventPublisherExecption;
	
	public boolean sendWelcomeMail(String id) throws DataNotFoundException, InvalidDataException, PersistenceException, EventPublisherExecption;

	public boolean changePassword(ChangePasswordDTO dto) throws DataNotFoundException, InvalidDataException, PersistenceException, EventPublisherExecption, InvalidCurrentPasswordException,CurrentAndNewPasswordEqualException;

	public LoginInfoDTO fetchUserSessionContext(String userid) throws DataNotFoundException, InvalidDataException; 
	
	public UserQuestionAnswers saveUserQuestionsAnswers(UserQuestionAnswers userQuestionAnswers) throws InvalidDataException, DuplicateRecordException;

	//public List<UserQuestionAnswers> findUserId(String userId) throws DataNotFoundException, InvalidDataException;

	public boolean checkUserQuestionsAnswers(UserQuestionAnswers userQuestionAnswers) throws DataNotFoundException, InvalidDataException, PersistenceException, EventPublisherExecption;
	
	public UserQuestionAnswers findUserByUserId(String loginId, boolean resetAnswer)throws DataNotFoundException, InvalidDataException;

	public UserQuestionAnswers getUserQuestions() throws RecordNotDataException;
	
	public void createUsersInAD() throws RecordNotDataException;
	
	public UserDto getUserIdAndName() throws RecordNotDataException;
	
	public Map<String, List<EmailDetailsDTO>> getUserDetailsAndAggrigatedEmail(String partnerId, String customerId) throws RecordNotDataException;

	public void createUserInAD(User user);

	public UserQuestionAnswers findUserQuestionByLoginId() throws DataNotFoundException;

	public PasswordExpiredDTO checkPasswordExpiry(String id)throws DataNotFoundException;

}
