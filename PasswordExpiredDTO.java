package com.sv.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PasswordExpiredDTO{
	private boolean success;
	private String message;
	private boolean ispasswordExpired;

	
	public PasswordExpiredDTO() {
		
	}
	
	public PasswordExpiredDTO(boolean success, String message, Boolean ispasswordExpired) {
		super();
		this.success = false;
		this.message = message;
		this.ispasswordExpired = ispasswordExpired;
	}

//	public PasswordExpiredDTO(String message, Boolean ispasswordExpired) {
//		super();
//		this.success = false;
//		this.message = message;
//		this.ispasswordExpired = ispasswordExpired;
//	}

	public boolean isSuccess() {
		return success;
	}

	public String getMessage() {
		return message;
	}

	public Boolean getData() {
		return ispasswordExpired;
	}


	public void setSuccess(boolean success) {
		this.success = success;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public void ispasswordExpired(Boolean passwordExpired) {
		this.ispasswordExpired = passwordExpired;
	}

	public String toJSON() {
	    try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
	}

}
