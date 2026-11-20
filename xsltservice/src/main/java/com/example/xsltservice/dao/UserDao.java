package com.example.xsltservice.dao;

import com.example.xsltservice.entity.User;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserDao {
    private final SessionFactory sessionFactory;

    @Autowired
    public UserDao(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public User save(User user) {
        sessionFactory.getCurrentSession().persist(user);
        return user;
    }

    public Optional<User> findById(long id) {
        return Optional.ofNullable(sessionFactory.getCurrentSession().find(User.class, id));
    }

    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return sessionFactory.getCurrentSession()
                .createSelectionQuery("from User u where u.username = :username", User.class)
                .setParameter("username", username)
                .uniqueResultOptional();
    }

    public List<User> findAll() {
        return sessionFactory.getCurrentSession()
                .createSelectionQuery("from User u order by u.id asc", User.class)
                .getResultList();
    }

    public boolean existsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        Long count = sessionFactory.getCurrentSession()
                .createSelectionQuery("select count(u.id) from User u where u.username = :username", Long.class)
                .setParameter("username", username)
                .getSingleResult();
        return count > 0;
    }

    public User update(User user) {
        return sessionFactory.getCurrentSession().merge(user);
    }

    public boolean deleteById(long id) {
        Session session = sessionFactory.getCurrentSession();
        User user = session.find(User.class, id);
        if (user == null) {
            return false;
        }
        session.remove(user);
        return true;
    }
}
