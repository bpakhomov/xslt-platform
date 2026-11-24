package com.example.xsltconverter.dao;

import com.example.xsltconverter.entity.AuditLog;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class AuditDao {

    private final SessionFactory sessionFactory;

    @Autowired
    public AuditDao(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void save(AuditLog auditLog) {
        sessionFactory.getCurrentSession().persist(auditLog);
    }
}
