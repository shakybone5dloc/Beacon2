-- Where a company posts its jobs, for the watchlist. Both null = not watched.
ALTER TABLE companies ADD COLUMN careers_platform varchar(20);
ALTER TABLE companies ADD COLUMN careers_board_id varchar(100);
CREATE INDEX ix_companies_watched ON companies (owner_id) WHERE careers_board_id IS NOT NULL;
