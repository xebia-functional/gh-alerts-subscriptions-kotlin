ALTER TABLE repositories
ADD CONSTRAINT unique_repos UNIQUE (owner, repository);