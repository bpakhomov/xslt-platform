package com.example.xsltservice.dao;

import com.example.xsltservice.entity.Conversion;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
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

    public List<Conversion> getConversionsForMigration(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Session session = sessionFactory.getCurrentSession();
        return session.createQuery(
                "from Conversion c where c.sourceFileKey is null " +
                        "and c.resultFileKey is null " +
                        "and c.sourceContent is not null and c.resultContent is not null ORDER BY c.id ASC",
                        Conversion.class)
                .setMaxResults(limit)
                .getResultList();
    }

    public void updateConversionsAfterMigration(List<UploadedConversion> uploadedConversions) {
        if (uploadedConversions == null || uploadedConversions.isEmpty()) {
            return;
        }
        Session session = sessionFactory.getCurrentSession();

        session.doWork(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE conversion SET source_file_key = ?, result_file_key = ?, source_content = NULL, " +
                            "result_content = NULL WHERE id = ?"
            )) {
                for (UploadedConversion conversion : uploadedConversions) {
                    ps.setString(1, conversion.sourceKey());
                    ps.setString(2, conversion.resultKey());
                    ps.setLong(3, conversion.id());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public List<Conversion> findByFilter(ConversionFilter filter) {
        Session session = sessionFactory.getCurrentSession();
        CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
        CriteriaQuery<Conversion> criteriaQuery = criteriaBuilder.createQuery(Conversion.class);
        Root<Conversion> root = criteriaQuery.from(Conversion.class);
        root.fetch("user", JoinType.LEFT);

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
        if (id == null) {
            return Optional.empty();
        }
        return sessionFactory.getCurrentSession()
                .createSelectionQuery("from Conversion c left join fetch c.user where c.id = :id", Conversion.class)
                .setParameter("id", id)
                .uniqueResultOptional();
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
