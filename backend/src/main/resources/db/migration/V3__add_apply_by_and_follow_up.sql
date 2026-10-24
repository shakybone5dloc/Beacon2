-- Calendar dates, not instants: "apply by Nov 15" means that day wherever you are.
ALTER TABLE job_applications ADD COLUMN apply_by date;
ALTER TABLE job_applications ADD COLUMN follow_up_on date;
