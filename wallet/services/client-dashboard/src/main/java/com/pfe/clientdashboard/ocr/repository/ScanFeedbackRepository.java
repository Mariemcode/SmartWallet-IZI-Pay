package com.pfe.clientdashboard.ocr.repository;

import com.pfe.clientdashboard.ocr.entities.ScanFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScanFeedbackRepository extends JpaRepository<ScanFeedback, Long> {

    Page<ScanFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<ScanFeedback> findByClientIdOrderByCreatedAtDesc(String clientId);

    /** Feedbacks pas encore traités par FeedbackLearningScheduler */
    List<ScanFeedback> findByProcessedAtIsNullOrderByCreatedAtAsc();

    /** Stats par fournisseur — calcul du taux de succès OCR */
    @Query("""
        SELECT f.ocrFournisseur,
               COUNT(f),
               COUNT(CASE WHEN f.valideSansCorrection = true THEN 1 END),
               COUNT(CASE WHEN f.montantCorrige = true THEN 1 END),
               COUNT(CASE WHEN f.fournisseurCorrige = true THEN 1 END),
               MAX(f.createdAt)
          FROM ScanFeedback f
         WHERE f.ocrFournisseur IS NOT NULL
         GROUP BY f.ocrFournisseur
         ORDER BY COUNT(f) DESC
    """)
    List<Object[]> statsByFournisseur();

    long countByValideSansCorrectionTrue();

    long countByCreatedAtAfter(LocalDateTime since);

    long count();
}
