package com.medicalreportapp.observations;

import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class DefaultUserProvider {

    private final JdbcTemplate jdbcTemplate;

    DefaultUserProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    UUID getDefaultUserId() {
        try {
            return jdbcTemplate.queryForObject("select id from users order by id limit 1", UUID.class);
        } catch (DataAccessException usersTableException) {
            return jdbcTemplate.queryForObject("select id from app_users order by id limit 1", UUID.class);
        }
    }
}
