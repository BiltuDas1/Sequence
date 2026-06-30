# Contributing to Sequence

## Development Workflow

- For detailed instructions on how to set up your local environment and build the project, please refer to the [Local Development Guide](docs/DEVELOPMENT.md).
- To understand the project structure and communication flow, refer to the [Architecture Overview](docs/ARCHITECTURE.md).

### Setup Custom Hooks

Use the following command to apply the custom hook, it blocks the commit if the commit message is not following a specific standard

```sh
git config core.hooksPath .githooks
```

# Commit Message Guidelines

Sequence enforces **Conventional Commits** to maintain a clean and automated history. Your commit messages must follow this regex-validated format:

`<type>(<optional scope>): <description>`

### Valid Types

- `feat`: A new feature.
- `fix`: A bug fix.
- `docs`: Documentation changes.
- `style`: Formatting/style changes.
- `refactor`: Code changes that neither fix a bug nor add a feature.
- `test`: Adding or updating tests.
- `chore`: Routine tasks like updating dependencies.
- `build`: Changes affecting the build system.
- `ci`: Changes to CI configurations.
- `perf`: Performance improvements.

**Example:** `feat(auth): add login functionality`
