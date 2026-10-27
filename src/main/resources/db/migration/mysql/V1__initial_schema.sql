-- 확인한 MySQL 스키마를 기준으로 작성한다. 데이터와 현재 AUTO_INCREMENT 값은 포함하지 않는다.

CREATE TABLE `users` (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `ball_balance` int NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nickname` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `profile_image` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `provider` enum('GOOGLE','KAKAO','NAVER') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `referral_code` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` enum('ROLE_ADMIN','ROLE_GUEST','ROLE_USER') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `uk_users_nickname` (`nickname`),
  UNIQUE KEY `UKdb3w2sgtsy1kf0qitc77h0bpa` (`referral_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `yarns` (
  `yarn_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `length` int DEFAULT NULL,
  `main_component` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `price` int NOT NULL,
  `sub_component` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `thickness` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `vendor` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `weight_g` int DEFAULT NULL,
  `ply` int DEFAULT NULL,
  `is_calculated_length` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`yarn_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `attendance_checks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `attendance_date` date NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attendance_user_date` (`user_id`,`attendance_date`),
  CONSTRAINT `FKs41ssc05xk1ha9c07ewa84gjv` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `credit_logs` (
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `amount` int NOT NULL,
  `type` enum('SIGNUP_BONUS','ATTENDANCE_DAILY','REFERRAL_BONUS','CHATROOM_ENTRY','ALT_YARN_VIEW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`log_id`),
  KEY `FKsfo24anott3tx20oavrgmr65l` (`user_id`),
  CONSTRAINT `FKsfo24anott3tx20oavrgmr65l` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `patterns` (
  `pattern_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `actual_size` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category_main` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category_sub` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `designer` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `gauge` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `needle_size` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `required_yarn_amount` varchar(1700) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `scraps_count` int NOT NULL,
  `size` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `thumbnail_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `view_count` int NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `yarn_id` bigint DEFAULT NULL,
  PRIMARY KEY (`pattern_id`),
  KEY `FKlwj77lr3bjg1oxnchd473k8s0` (`user_id`),
  KEY `FKf34ujvcowkuhghata9bxloor3` (`yarn_id`),
  CONSTRAINT `FKf34ujvcowkuhghata9bxloor3` FOREIGN KEY (`yarn_id`) REFERENCES `yarns` (`yarn_id`),
  CONSTRAINT `FKlwj77lr3bjg1oxnchd473k8s0` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `referral_registrations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `referee_id` bigint NOT NULL,
  `referrer_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_referral_registrations_referee` (`referee_id`),
  KEY `FK7ce1y9ny86gpr38hgu62h67xo` (`referrer_id`),
  CONSTRAINT `FK7ce1y9ny86gpr38hgu62h67xo` FOREIGN KEY (`referrer_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FKiiftuii33r527ik0mwtc9963r` FOREIGN KEY (`referee_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `unlocks` (
  `unlock_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `pattern_id` bigint NOT NULL,
  `type` enum('CHAT','YARN_INFO') COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`unlock_id`),
  UNIQUE KEY `uk_unlocks_user_pattern_type` (`user_id`,`pattern_id`,`type`),
  CONSTRAINT `FK44l33vcxe9balvgt3u1o30br4` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_interests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `keyword` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKdv9fflrh61wyuujfwx2yn1tb4` (`user_id`),
  CONSTRAINT `FKdv9fflrh61wyuujfwx2yn1tb4` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `yarn_alternatives` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `component_score` int DEFAULT NULL,
  `gauge_score` int DEFAULT NULL,
  `length_score` int DEFAULT NULL,
  `needle_score` int DEFAULT NULL,
  `ranking` int NOT NULL,
  `alternative_yarn_id` bigint NOT NULL,
  `original_yarn_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_yarn_alternative_original_alternative` (`original_yarn_id`,`alternative_yarn_id`),
  KEY `FKmnm2il7fd68pxojxmbl7qgsyg` (`alternative_yarn_id`),
  CONSTRAINT `FKmnm2il7fd68pxojxmbl7qgsyg` FOREIGN KEY (`alternative_yarn_id`) REFERENCES `yarns` (`yarn_id`),
  CONSTRAINT `FKt8ludaihd8cuuobq90q62rhkx` FOREIGN KEY (`original_yarn_id`) REFERENCES `yarns` (`yarn_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `chat_rooms` (
  `chat_room_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `segment_end_at` datetime(6) NOT NULL,
  `segment_start_at` datetime(6) NOT NULL,
  `pattern_id` bigint NOT NULL,
  PRIMARY KEY (`chat_room_id`),
  UNIQUE KEY `uk_chat_room_pattern_segment_start` (`pattern_id`,`segment_start_at`),
  CONSTRAINT `FKoyru1ia4k2901f3w03v9dauet` FOREIGN KEY (`pattern_id`) REFERENCES `patterns` (`pattern_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pattern_alternative_yarns` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `component_score` int DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `gauge_score` int DEFAULT NULL,
  `length_score` int DEFAULT NULL,
  `needle_score` int DEFAULT NULL,
  `ranking` int DEFAULT NULL,
  `pattern_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `yarn_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKe30f96e2td3phcghj46h8bdbm` (`pattern_id`),
  KEY `FKhfl6lqqlciei70lcutvgsfrqr` (`user_id`),
  KEY `FKngma9thog863k0qy8ti3xlspy` (`yarn_id`),
  CONSTRAINT `FKe30f96e2td3phcghj46h8bdbm` FOREIGN KEY (`pattern_id`) REFERENCES `patterns` (`pattern_id`),
  CONSTRAINT `FKhfl6lqqlciei70lcutvgsfrqr` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FKngma9thog863k0qy8ti3xlspy` FOREIGN KEY (`yarn_id`) REFERENCES `yarns` (`yarn_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pattern_images` (
  `image_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `image_order` int DEFAULT NULL,
  `image_url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `pattern_id` bigint NOT NULL,
  PRIMARY KEY (`image_id`),
  KEY `FKinbi1059k7k49j03rofwgcfcm` (`pattern_id`),
  CONSTRAINT `FKinbi1059k7k49j03rofwgcfcm` FOREIGN KEY (`pattern_id`) REFERENCES `patterns` (`pattern_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pattern_interest_numbers` (
  `pattern_id` bigint NOT NULL,
  `interest_number` int NOT NULL,
  KEY `FK7ue5yi4udrxtrexq24ebx2eji` (`pattern_id`),
  CONSTRAINT `FK7ue5yi4udrxtrexq24ebx2eji` FOREIGN KEY (`pattern_id`) REFERENCES `patterns` (`pattern_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pattern_original_yarns` (
  `pattern_original_yarn_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `main_yarn_id` bigint NOT NULL,
  `pattern_id` bigint NOT NULL,
  `second_yarn_id` bigint DEFAULT NULL,
  `sub_yarn_id` bigint DEFAULT NULL,
  PRIMARY KEY (`pattern_original_yarn_id`),
  KEY `FK4u8g7b7tobowmjb4pb9b6556o` (`main_yarn_id`),
  KEY `FK56cv3vtpixub9iw7cna0y6ccm` (`pattern_id`),
  KEY `FKennkerscbmai3b1av88d2s0mr` (`second_yarn_id`),
  KEY `FK2ovh4q4hqv3ahy2yq79g0v3ta` (`sub_yarn_id`),
  CONSTRAINT `FK2ovh4q4hqv3ahy2yq79g0v3ta` FOREIGN KEY (`sub_yarn_id`) REFERENCES `yarns` (`yarn_id`),
  CONSTRAINT `FK4u8g7b7tobowmjb4pb9b6556o` FOREIGN KEY (`main_yarn_id`) REFERENCES `yarns` (`yarn_id`),
  CONSTRAINT `FK56cv3vtpixub9iw7cna0y6ccm` FOREIGN KEY (`pattern_id`) REFERENCES `patterns` (`pattern_id`),
  CONSTRAINT `FKennkerscbmai3b1av88d2s0mr` FOREIGN KEY (`second_yarn_id`) REFERENCES `yarns` (`yarn_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pattern_view_logs` (
  `pattern_view_log_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `viewed_date` date NOT NULL,
  `pattern_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`pattern_view_log_id`),
  UNIQUE KEY `uk_pattern_user_date` (`pattern_id`,`user_id`,`viewed_date`),
  KEY `FK47uwqb40iva9vpc2x089i2b66` (`user_id`),
  CONSTRAINT `FK47uwqb40iva9vpc2x089i2b66` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FKhhudk9rxkv4e4fvrqdsbiy6ce` FOREIGN KEY (`pattern_id`) REFERENCES `patterns` (`pattern_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `scraps` (
  `scrap_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `style_id` bigint DEFAULT NULL,
  `pattern_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`scrap_id`),
  UNIQUE KEY `uk_scrap_user_pattern` (`user_id`,`pattern_id`),
  KEY `FKmrn49tujo19ob4mp9gcuido8u` (`pattern_id`),
  CONSTRAINT `FKmrn49tujo19ob4mp9gcuido8u` FOREIGN KEY (`pattern_id`) REFERENCES `patterns` (`pattern_id`),
  CONSTRAINT `FKqd3nh3tj8ru0ubnk54qckuh42` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `yarn_alternative_comments` (
  `comment_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `content` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `yarn_alternative_id` bigint NOT NULL,
  PRIMARY KEY (`comment_id`),
  KEY `FKthjq72enhq2m8ci8f4rbam3ux` (`user_id`),
  KEY `FKj7usy563v61dn3xviiv45h9km` (`yarn_alternative_id`),
  CONSTRAINT `FKj7usy563v61dn3xviiv45h9km` FOREIGN KEY (`yarn_alternative_id`) REFERENCES `yarn_alternatives` (`id`),
  CONSTRAINT `FKthjq72enhq2m8ci8f4rbam3ux` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `yarn_alternative_reactions` (
  `reaction_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `type` enum('CANCEL','DISLIKE','LIKE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `yarn_alternative_id` bigint NOT NULL,
  PRIMARY KEY (`reaction_id`),
  UNIQUE KEY `uk_yarn_alt_reaction_user` (`yarn_alternative_id`,`user_id`),
  KEY `FK1rtr9b44cq31f3390brt7qm3u` (`user_id`),
  CONSTRAINT `FK1rtr9b44cq31f3390brt7qm3u` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FK30gx3w75hh9uhoyk4noc33p2y` FOREIGN KEY (`yarn_alternative_id`) REFERENCES `yarn_alternatives` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `chat_messages` (
  `chat_message_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `text` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reply_to_message_id` bigint DEFAULT NULL,
  `chat_room_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`chat_message_id`),
  KEY `FKljkvrb31ewq9e6e874spg4v1v` (`reply_to_message_id`),
  KEY `FKbcsxusjp1v4rd8879fhvq8ssb` (`chat_room_id`),
  KEY `FK6f0y4l43ihmgfswkgy9yrtjkh` (`user_id`),
  CONSTRAINT `FK6f0y4l43ihmgfswkgy9yrtjkh` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FKbcsxusjp1v4rd8879fhvq8ssb` FOREIGN KEY (`chat_room_id`) REFERENCES `chat_rooms` (`chat_room_id`),
  CONSTRAINT `FKljkvrb31ewq9e6e874spg4v1v` FOREIGN KEY (`reply_to_message_id`) REFERENCES `chat_messages` (`chat_message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `chat_read_statuses` (
  `chat_read_status_id` bigint NOT NULL AUTO_INCREMENT,
  `last_read_message_id` bigint DEFAULT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `chat_room_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`chat_read_status_id`),
  KEY `FKaq3yblqibnrxhvgt8bkeelgu2` (`chat_room_id`),
  KEY `FKk8jdyxsryoebg3le1qyyg5jxs` (`user_id`),
  CONSTRAINT `FKaq3yblqibnrxhvgt8bkeelgu2` FOREIGN KEY (`chat_room_id`) REFERENCES `chat_rooms` (`chat_room_id`),
  CONSTRAINT `FKk8jdyxsryoebg3le1qyyg5jxs` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `chat_room_statuses` (
  `chat_room_status_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `favorite` bit(1) NOT NULL,
  `hidden` bit(1) NOT NULL,
  `chat_room_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `nickname` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`chat_room_status_id`),
  UNIQUE KEY `uk_chat_room_status_user_room` (`user_id`,`chat_room_id`),
  UNIQUE KEY `uk_chat_room_status_room_nickname` (`chat_room_id`,`nickname`),
  CONSTRAINT `FKffemg0hvhtctr0ogp80pwgxvl` FOREIGN KEY (`chat_room_id`) REFERENCES `chat_rooms` (`chat_room_id`),
  CONSTRAINT `FKhwm6up7etwmber0up32rcy52i` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
