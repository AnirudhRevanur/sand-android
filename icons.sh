for dir in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  mkdir -p app/src/main/res/mipmap-$dir
done

magick icon.png -resize 48x48 app/src/main/res/mipmap-mdpi/ic_launcher.png
magick icon.png -resize 72x72 app/src/main/res/mipmap-hdpi/ic_launcher.png
magick icon.png -resize 96x96 app/src/main/res/mipmap-xhdpi/ic_launcher.png
magick icon.png -resize 144x144 app/src/main/res/mipmap-xxhdpi/ic_launcher.png
magick icon.png -resize 192x192 app/src/main/res/mipmap-xxxhdpi/ic_launcher.png

# Same for round
for dir in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  cp app/src/main/res/mipmap-$dir/ic_launcher.png \
    app/src/main/res/mipmap-$dir/ic_launcher_round.png
done
