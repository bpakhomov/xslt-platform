package com.example.xsltservice.dao;

import com.example.xsltservice.entity.Conversion;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ConversionDao {
    private final SessionFactory sessionFactory;

    @Autowired
    public ConversionDao(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void createConversion(
            Conversion conversion
    ) {
        Session session = sessionFactory.getCurrentSession();
        session.persist(conversion);
    }

}
