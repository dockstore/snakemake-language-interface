# By convention, the first pseudorule should be called "all"
# We're using the expand() function to create multiple targets
rule all:
	input:
		expand(
			"{cheer}/world.txt",
			cheer = ['Bonjour', 'Ciao', 'Hello', 'Hola'],
		),

# First real rule, this is using a wildcard called "cheer"
rule multilingual_hello_world:
	output:
		"{cheer}/world.txt",
	shell:
		"""
		echo "{wildcards.cheer}, World!" > {output}
		"""
