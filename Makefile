# The commands this project is actually run with. Every one of them is what CI runs too,
# so a green pipeline and a green laptop mean the same thing.

MVN := ./mvnw -B -ntp

.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

.PHONY: test
test: ## Run the suite
	$(MVN) test

.PHONY: verify
verify: ## Run the suite and enforce coverage
	$(MVN) verify

.PHONY: coverage
coverage: verify ## Run the suite and open the coverage report
	@echo "target/site/jacoco/index.html"

.PHONY: golden
golden: ## Rewrite the conversation transcripts, then read the diff
	$(MVN) test -Dtest=GoldenConversationTest -Dgolden.update=true
	@git --no-pager diff --stat -- src/test/resources/golden

.PHONY: console
console: ## Talk to the agent in a terminal
	$(MVN) spring-boot:run -Dspring-boot.run.profiles=console

.PHONY: run
run: ## Start the service on :8080 with an embedded database
	$(MVN) spring-boot:run

.PHONY: image
image: ## Build the container image
	docker build -t municipality-agent:local .

.PHONY: up
up: ## Bring up the service and a Postgres beside it
	docker compose up --build -d

.PHONY: logs
logs: ## Follow the service log
	docker compose logs -f agent

.PHONY: down
down: ## Take it all down, database included
	docker compose down -v

.PHONY: security
security: ## Check the dependencies against the vulnerability database
	$(MVN) -Psecurity verify

.PHONY: clean
clean: ## Remove everything the build produced
	$(MVN) clean
