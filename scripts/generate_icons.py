import os
import sys
from PIL import Image

def generate_icons(source_image_path):
    # Base output path
    res_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'app', 'src', 'main', 'res')
    
    # Standard launcher icon sizes
    sizes = {
        'mdpi': 48,
        'hdpi': 72,
        'xhdpi': 96,
        'xxhdpi': 144,
        'xxxhdpi': 192
    }
    
    try:
        with Image.open(source_image_path) as img:
            # Ensure it's square
            width, height = img.size
            if width != height:
                print("Warning: Source image is not square. Icons may look distorted.")
            
            # Generate legacy icons and round icons
            for density, size in sizes.items():
                out_dir = os.path.join(res_dir, f'mipmap-{density}')
                os.makedirs(out_dir, exist_ok=True)
                
                # Standard icon
                resized = img.resize((size, size), Image.Resampling.LANCZOS)
                resized.save(os.path.join(out_dir, 'ic_launcher.png'), 'PNG')
                
                # Round icon (we'll just use the same or try to mask it if we had a mask)
                # For simplicity, we just save the same image for round since Android handles adaptive icons mostly
                resized.save(os.path.join(out_dir, 'ic_launcher_round.png'), 'PNG')
                
                # Adaptive icon foreground (often larger, e.g. 108x108 dp, but we'll use a scaled version)
                fg_size = int(size * (108/48)) # Scale appropriately
                fg_resized = img.resize((fg_size, fg_size), Image.Resampling.LANCZOS)
                fg_resized.save(os.path.join(out_dir, 'ic_launcher_foreground.png'), 'PNG')
                
            print("Successfully generated all launcher icons!")
    except Exception as e:
        print(f"Error generating icons: {e}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python generate_icons.py <path_to_source_image.png>")
    else:
        generate_icons(sys.argv[1])
