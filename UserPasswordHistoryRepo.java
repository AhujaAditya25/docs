package com.sv.user.repo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.sv.user.entity.UserPasswordHistory;

@Repository
public interface UserPasswordHistoryRepo extends MongoRepository<UserPasswordHistory, String> {

	public UserPasswordHistory findFirstByUserIdOrderByCreateDateDesc(String id);
}