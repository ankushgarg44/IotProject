with open("app/src/main/java/com/example/ebike/HomeScreen.kt", "r") as f:
    text = f.read()

# Remove the duplicate GoogleMap SimplifiedMapScreen at the bottom
start_str = "// Create a simplified map component for the home screen card without search bar"
idx = text.find(start_str)

if idx != -1:
    text = text[:idx]

with open("app/src/main/java/com/example/ebike/HomeScreen.kt", "w") as f:
    f.write(text)
print("Fixed")
