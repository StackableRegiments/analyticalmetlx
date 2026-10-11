# Contributing Guidelines

## Branching Strategy
- `master` — legacy MeTL code  
- `test` — chat prototype development  
- `integration` — upcoming merge into MeTL  

---

## Commit Messages
Use clear, descriptive commit messages:
feat: add typing indicator
fix: resolve socket double-join issue
docs: update README.md

---

## Pull Requests
1. Ensure code builds without errors  
2. Include screenshots for UI changes  
3. Reference related issues or rubric items  
4. Request review before merging  

---

## Code Style
- Use ES6+ syntax  
- Keep components small and modular  
- Maintain consistent formatting  
- Document complex functions  

---

## Testing
- Test with two browser windows  
- Verify pgAdmin activity during message flow  
- Confirm reactions and replies propagate correctly 