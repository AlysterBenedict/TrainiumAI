import os
import sys
import urllib.request

def download_file(url, filename):
    print(f"Downloading {url} to {filename}...")
    
    # Progress reporter callback
    def reporthook(blocknum, blocksize, totalsize):
        readsofar = blocknum * blocksize
        if totalsize > 0:
            percent = readsofar * 1e2 / totalsize
            s = f"\rProgress: {percent:.2f}% ({readsofar / (1024*1024):.1f} MB of {totalsize / (1024*1024):.1f} MB)"
            sys.stdout.write(s)
            sys.stdout.flush()
        else:
            sys.stdout.write(f"\rRead {readsofar / (1024*1024):.1f} MB")
            sys.stdout.flush()

    try:
        # standard urlretrieve will follow redirects automatically
        urllib.request.urlretrieve(url, filename, reporthook)
        print("\nDownload complete successfully!")
    except Exception as e:
        print(f"\nDownload failed: {e}")
        # Clean up partial file on failure
        if os.path.exists(filename):
            os.remove(filename)
        sys.exit(1)

if __name__ == '__main__':
    url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    dest_dir = "app/src/main/assets"
    
    # Ensure assets directory exists
    os.makedirs(dest_dir, exist_ok=True)
    dest_path = os.path.join(dest_dir, "gemma-4-E2B-it.litertlm")
    
    download_file(url, dest_path)
