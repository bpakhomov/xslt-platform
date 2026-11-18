package com.example.xsltservice.dao;

import com.example.xsltservice.entity.Conversion;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ConversionDao {
    private final SessionFactory sessionFactory;

    @Autowired
    public ConversionDao(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public List<Conversion> findByFilter(ConversionFilter filter) {
        Session session = sessionFactory.getCurrentSession();
        CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
        CriteriaQuery<Conversion> criteriaQuery = criteriaBuilder.createQuery(Conversion.class);
        Root<Conversion> root = criteriaQuery.from(Conversion.class);

        List<Predicate> predicates = new ArrayList<>();
        if (filter.conversionType() != null) {
            predicates.add(criteriaBuilder.equal(root.get("conversionType"), filter.conversionType()));
        }
        addRange(predicates, criteriaBuilder, root.get("conversionDateTime"), filter.dateFrom(), filter.dateTo());
        addRange(predicates, criteriaBuilder, root.get("conversionTime"), filter.conversionTimeFrom(), filter.conversionTimeTo());
        addRange(predicates, criteriaBuilder, root.get("inputElementsCount"), filter.inputElementsCountFrom(), filter.inputElementsCountTo());
        addRange(predicates, criteriaBuilder, root.get("outputElementsCount"), filter.outputElementsCountFrom(), filter.outputElementsCountTo());

        criteriaQuery.select(root)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(
                        criteriaBuilder.desc(root.get("conversionDateTime")),
                        criteriaBuilder.desc(root.get("id"))
                );

        return session.createSelectionQuery(criteriaQuery)
                .setFirstResult(filter.offset())
                .setMaxResults(filter.limit())
                .getResultList();
    }

    public Optional<Conversion> findById(Long id) {
        return Optional.ofNullable(sessionFactory.getCurrentSession().find(Conversion.class, id));
    }

    public Conversion save(Conversion conversion) {
        sessionFactory.getCurrentSession().persist(conversion);
        return conversion;
    }

    private <T extends Comparable<? super T>> void addRange(
            List<Predicate> predicates,
            CriteriaBuilder criteriaBuilder,
            Path<T> path,
            T from,
            T to
    ) {
        if (from != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(path, from));
        }
        if (to != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(path, to));
        }
    }
}
