ALTER TABLE user
    ADD COLUMN profile_image VARCHAR(2048) NOT NULL
        DEFAULT 'https://api.dicebear.com/9.x/bottts/svg?seed=default' AFTER nickname;

ALTER TABLE user_detail
    DROP COLUMN profile_image;
