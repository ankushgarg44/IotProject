#!/bin/bash

# Create temporary directory
mkdir -p app/src/main/res/temp/icons

# Copy bicycle.png to temporary directory
cp app/src/main/res/drawable/bicycle.png app/src/main/res/temp/icons/icon.png

# Dimensions for different densities
declare -A sizes=(
  ["mdpi"]=48
  ["hdpi"]=72
  ["xhdpi"]=96
  ["xxhdpi"]=144
  ["xxxhdpi"]=192
)

echo "To generate the icons properly, you'll need ImageMagick installed."
echo "You can manually generate the icons from bicycle.png at different sizes:"

for density in "${!sizes[@]}"; do
  size=${sizes[$density]}
  echo "For $density icons: resize bicycle.png to ${size}x${size} pixels"
  echo "Save as ic_launcher.webp and ic_launcher_round.webp in app/src/main/res/mipmap-$density/"
done

echo "You can use an online tool like Android Asset Studio to generate these icons from bicycle.png" 