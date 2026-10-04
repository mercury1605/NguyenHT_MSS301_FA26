package com.fudn.movieservice.repository;

import com.fudn.movieservice.model.Showtime;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ShowtimeRepository extends MongoRepository<Showtime, String> {

    boolean existsByRoomId(String roomId);

    boolean existsByMovieId(String movieId);

    List<Showtime> findAllByOrderByStartTimeAsc();

    List<Showtime> findByMovieIdOrderByStartTimeAsc(String movieId);
}
